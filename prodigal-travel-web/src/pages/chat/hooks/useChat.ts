import { message } from 'antd';
import { isAxiosError } from 'axios';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { history, useSearchParams } from 'umi';
import {
  conversations as fetchConversations,
  deleteConversationByChatId as deleteConversationApi,
} from '@/api/travelAssistant';
import { useAuth } from '@/contexts/AuthContext';
import type { ChatMessage, ConversationRecord } from '@/types';
import { assertBaseResultOk, unwrapBaseResult } from '@/utils/apiResult';
import {
  clearManusPersistedState,
  loadManusPersistedState,
  saveManusPersistedState,
} from '@/utils/manusChatStorage';
import { summarizeManusSteps } from '@/utils/manusStepSummary';
import { routeManusSsePayload } from '@/utils/manusStreamRouting';
import { useStreaming, type StreamChatKind } from './useStreaming';

const genId = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const PENDING_PREFIX = 'pending-';
const TYPEWRITER_TICK_MS = 24;
const TYPEWRITER_CHARS_PER_TICK = 4;

const defaultTitleFromMessage = (text: string): string => {
  const t = text.replace(/\s+/g, ' ').trim();
  if (!t) return '新对话';
  return t.length > 24 ? `${t.slice(0, 24)}…` : t;
};

const sortConversations = (
  list: ConversationRecord[]
): ConversationRecord[] =>
  [...list].sort((a, b) => b.updatedAt - a.updatedAt);

/** 解析接口里的时间字段（ISO 字符串、毫秒/秒数字）。 */
function parseApiTimeMs(raw: string | number | undefined | null): number | undefined {
  if (raw == null) return undefined;
  if (typeof raw === 'number' && Number.isFinite(raw)) {
    return raw > 1e11 ? raw : raw * 1000;
  }
  const s = String(raw).trim();
  if (!s) return undefined;
  if (/^\d+$/.test(s)) {
    const n = Number(s);
    return n > 1e11 ? n : n * 1000;
  }
  const t = Date.parse(s);
  return Number.isNaN(t) ? undefined : t;
}

function mapApiMessage(m: API.ChatMessage): ChatMessage {
  const r = (m.role ?? '').toLowerCase();
  const role: ChatMessage['role'] =
    r === 'user' ? 'user' : r === 'system' ? 'system' : 'assistant';
  let createdAt = Date.now();
  if (m.createTime != null) {
    const t = new Date(String(m.createTime)).getTime();
    if (!Number.isNaN(t)) createdAt = t;
  }
  return {
    id: m.id ?? genId(),
    role,
    content: m.content ?? '',
    createdAt,
  };
}

function mapApiMessages(
  list: API.ChatMessage[] | undefined
): ChatMessage[] {
  return (list ?? [])
    .map((m) => mapApiMessage(m))
    .filter((m) => m.role === 'user' || m.role === 'assistant');
}

/** 列表接口：仅元数据，messages 为空 */
function voToListRecord(vo: API.ChatMessageVO, orderIndex: number): ConversationRecord {
  const cid = vo.conversationId ?? '';
  const updatedAt =
    parseApiTimeMs(vo.updateTime) ?? Date.now() - orderIndex;
  return {
    id: cid,
    backendChatId: cid,
    title: (vo.title ?? '').trim() || '对话',
    messages: [],
    updatedAt,
  };
}

/** 详情接口：含完整消息 */
function voToDetailRecord(vo: API.ChatMessageVO): ConversationRecord {
  const cid = vo.conversationId ?? '';
  const msgs = mapApiMessages(vo.messages);
  let fromMessages = 0;
  for (const m of msgs) {
    if (m.createdAt > fromMessages) fromMessages = m.createdAt;
  }
  const updatedAt =
    parseApiTimeMs(vo.updateTime) ?? (fromMessages > 0 ? fromMessages : Date.now());
  return {
    id: cid,
    backendChatId: cid,
    title: (vo.title ?? '').trim() || '对话',
    messages: msgs,
    updatedAt,
  };
}

function finalizeManusAssistantMessage(m: ChatMessage): ChatMessage {
  const base = { ...m, streaming: false };
  if (base.content?.trim()) return base;
  const steps = base.manusSteps ?? [];
  if (steps.length === 0) return base;
  return { ...base, content: summarizeManusSteps(steps) };
}

const formatChatError = (e: unknown): string => {
  if (isAxiosError(e)) {
    const status = e.response?.status;
    if (status === 504 || status === 502 || status === 503) {
      return '网关超时或服务暂不可用（502/503/504）。';
    }
    if (status === 404) {
      return '接口不存在（404）。';
    }
    if (status === 401 || status === 403) {
      return '无权限调用接口（401/403），请检查登录或请求头。';
    }
    if (status === 400) {
      return '请求参数不合法（400），请确认已填写有效问题内容。';
    }
    const data = e.response?.data;
    if (data && typeof data === 'object' && 'msg' in data) {
      const m = (data as { msg?: unknown }).msg;
      if (typeof m === 'string' && m.trim()) return m;
    }
    return e.message || '请求失败';
  }
  if (e instanceof Error) return e.message;
  return '请求失败，请检查网络或后端服务';
};

export interface UseChatOptions {
  /** 旅游助手：SSE MCP 流式；超级智能体：`/travel/manus/chat` 分步流式 */
  mode: StreamChatKind;
}

export function useChat(options: UseChatOptions) {
  const { mode } = options;
  const { token, ready } = useAuth();
  const [searchParams] = useSearchParams();
  const { runStream, runManusStream, abort } = useStreaming();
  const [conversations, setConversations] = useState<ConversationRecord[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const activeIdRef = useRef<string | null>(null);
  const typewriterRef = useRef<{
    timer: number | null;
    convId: string | null;
    messageId: string | null;
    buffer: string;
  }>({ timer: null, convId: null, messageId: null, buffer: '' });

  useEffect(() => {
    activeIdRef.current = activeId;
  }, [activeId]);

  const stopTypewriter = useCallback(() => {
    const tw = typewriterRef.current;
    if (tw.timer != null) {
      window.clearInterval(tw.timer);
    }
    typewriterRef.current = { timer: null, convId: null, messageId: null, buffer: '' };
  }, []);

  const mergeDetailIntoList = useCallback(
    (chatId: string, rec: ConversationRecord) => {
      setConversations((prev) => {
        const idx = prev.findIndex((c) => c.id === chatId);
        if (idx === -1) {
          return sortConversations([rec, ...prev]);
        }
        const next = [...prev];
        next[idx] = {
          ...next[idx],
          title: rec.title,
          messages: rec.messages,
          updatedAt: rec.updatedAt,
        };
        return sortConversations(next);
      });
    },
    []
  );

  const loadConversationDetail = useCallback(
    async (chatId: string) => {
      if (mode === 'manus') return;
      if (!ready || !token || chatId.startsWith(PENDING_PREFIX)) return;
      try {
        const res = await fetchConversations({ chatId });
        const rows = unwrapBaseResult<API.ChatMessageVO[]>(res.data);
        const vo = rows[0];
        if (vo) {
          mergeDetailIntoList(chatId, voToDetailRecord(vo));
        }
      } catch {
        message.error('加载会话消息失败');
      }
    },
    [mergeDetailIntoList, mode, ready, token]
  );

  /**
   * 全量拉会话列表 + 当前选中会话详情。
   * 仅用于登录后首屏；不在每条聊天消息后调用，避免重复打 MySQL、侧栏长时间 loading。
   */
  const refreshConversationList = useCallback(async () => {
    if (!ready || !token) return;
    setListLoading(true);
    try {
      const res = await fetchConversations({});
      const rows = unwrapBaseResult<API.ChatMessageVO[]>(res.data);
      const mapped = rows.map((vo, i) => voToListRecord(vo, i));
      const currentActive = activeIdRef.current;
      setConversations(mapped);
      setListLoading(false);

      if (mapped.length === 0) {
        setActiveId(null);
        return;
      }
      const targetId =
        currentActive && mapped.some((c) => c.id === currentActive)
          ? currentActive
          : mapped[0].id;
      setActiveId(targetId);
      void loadConversationDetail(targetId);
    } catch {
      message.error('加载会话列表失败');
      setConversations([]);
      setActiveId(null);
      setListLoading(false);
    }
  }, [loadConversationDetail, ready, token]);

  const refreshListRef = useRef(refreshConversationList);
  refreshListRef.current = refreshConversationList;

  /**
   * 旅游助手：拉 `/travel/conversations`。
   * 超级智能体：仅用本地持久化，且与旅游助手列表完全隔离；从首页带 `?fresh=1` 进入时清空并开始新会话。
   */
  useEffect(() => {
    if (!ready) {
      return;
    }
    if (mode === 'manus') {
      if (searchParams.get('fresh') === '1') {
        setConversations([]);
        setActiveId(null);
        clearManusPersistedState();
        history.replace('/chat/manus');
        return;
      }
      if (!token) {
        setConversations([]);
        setActiveId(null);
        setListLoading(false);
        return;
      }
      setListLoading(false);
      const { conversations: list, activeId: active } = loadManusPersistedState();
      setConversations(list);
      setActiveId(active);
      return;
    }
    if (!token) {
      setConversations([]);
      setActiveId(null);
      setListLoading(false);
      return;
    }
    void refreshListRef.current();
  }, [token, ready, mode, searchParams]);

  useEffect(() => {
    if (mode !== 'manus' || !token) return;
    saveManusPersistedState({ conversations, activeId });
  }, [mode, token, conversations, activeId]);

  const persist = useCallback(
    (updater: (prev: ConversationRecord[]) => ConversationRecord[]) => {
      setConversations((prev) => sortConversations(updater(prev)));
    },
    []
  );

  // 逐字追加到指定 assistant 消息，模拟「打字机效果」
  const enqueueTypewriter = useCallback(
    (args: { convId: string; messageId: string; delta: string }) => {
      const { convId, messageId, delta } = args;
      if (!delta) return;
      const tw = typewriterRef.current;

      // 若目标消息变更（新会话/新 assistant 消息），直接切换并清空旧缓冲
      if (tw.convId !== convId || tw.messageId !== messageId) {
        stopTypewriter();
        typewriterRef.current = { timer: null, convId, messageId, buffer: delta };
      } else {
        tw.buffer += delta;
      }

      const startTimer = () => {
        const cur = typewriterRef.current;
        if (cur.timer != null) return;
        cur.timer = window.setInterval(() => {
          const s = typewriterRef.current;
          if (!s.convId || !s.messageId) return;
          if (!s.buffer) {
            stopTypewriter();
            return;
          }
          const take = s.buffer.slice(0, TYPEWRITER_CHARS_PER_TICK);
          s.buffer = s.buffer.slice(TYPEWRITER_CHARS_PER_TICK);
          persist((prev) => {
            const idx = prev.findIndex((c) => c.id === s.convId);
            if (idx === -1) return prev;
            const c = prev[idx];
            const nextMessages = c.messages.map((m) =>
              m.id === s.messageId ? { ...m, content: m.content + take } : m
            );
            const nextRecord: ConversationRecord = {
              ...c,
              messages: nextMessages,
              updatedAt: Date.now(),
            };
            return [...prev.slice(0, idx), nextRecord, ...prev.slice(idx + 1)];
          });
        }, TYPEWRITER_TICK_MS);
      };

      startTimer();
    },
    [persist, stopTypewriter]
  );

  const flushTypewriter = useCallback(() => {
    const tw = typewriterRef.current;
    if (!tw.convId || !tw.messageId || !tw.buffer) return;
    const rest = tw.buffer;
    tw.buffer = '';
    persist((prev) => {
      const idx = prev.findIndex((c) => c.id === tw.convId);
      if (idx === -1) return prev;
      const c = prev[idx];
      const nextMessages = c.messages.map((m) =>
        m.id === tw.messageId ? { ...m, content: m.content + rest } : m
      );
      const nextRecord: ConversationRecord = {
        ...c,
        messages: nextMessages,
        updatedAt: Date.now(),
      };
      return [...prev.slice(0, idx), nextRecord, ...prev.slice(idx + 1)];
    });
    stopTypewriter();
  }, [persist, stopTypewriter]);

  const activeConversation = useMemo(() => {
    if (!activeId) return null;
    return conversations.find((c) => c.id === activeId) ?? null;
  }, [activeId, conversations]);

  const messages = activeConversation?.messages ?? [];

  const newChat = useCallback(() => {
    abort();
    stopTypewriter();
    const id = `${PENDING_PREFIX}${genId()}`;
    const now = Date.now();
    const record: ConversationRecord = {
      id,
      title: '新对话',
      messages: [],
      updatedAt: now,
    };
    persist((prev) => [record, ...prev]);
    setActiveId(id);
  }, [abort, persist]);

  const selectChat = useCallback(
    (id: string) => {
      abort();
      stopTypewriter();
      setActiveId(id);
      if (mode === 'travel' && ready && token && !id.startsWith(PENDING_PREFIX)) {
        void loadConversationDetail(id);
      }
    },
    [abort, loadConversationDetail, mode, ready, stopTypewriter, token]
  );

  const deleteChat = useCallback(
    async (id: string) => {
      abort();
      stopTypewriter();
      if (id.startsWith(PENDING_PREFIX)) {
        let nextList: ConversationRecord[] = [];
        setConversations((prev) => {
          nextList = prev.filter((c) => c.id !== id);
          return nextList;
        });
        setActiveId((cur) => {
          if (cur !== id) return cur;
          return sortConversations(nextList)[0]?.id ?? null;
        });
        return;
      }
      if (!ready || !token) {
        let nextList: ConversationRecord[] = [];
        setConversations((prev) => {
          nextList = prev.filter((c) => c.id !== id);
          return nextList;
        });
        setActiveId((cur) => {
          if (cur !== id) return cur;
          return sortConversations(nextList)[0]?.id ?? null;
        });
        return;
      }
      if (mode === 'manus') {
        let nextList: ConversationRecord[] = [];
        setConversations((prev) => {
          nextList = prev.filter((c) => c.id !== id);
          return nextList;
        });
        setActiveId((cur) => {
          if (cur !== id) return cur;
          return sortConversations(nextList)[0]?.id ?? null;
        });
        return;
      }
      try {
        const res = await deleteConversationApi(id);
        assertBaseResultOk(res.data);
        let nextList: ConversationRecord[] = [];
        setConversations((prev) => {
          nextList = prev.filter((c) => c.id !== id);
          return nextList;
        });
        setActiveId((cur) => {
          if (cur !== id) return cur;
          return sortConversations(nextList)[0]?.id ?? null;
        });
      } catch (e) {
        message.error(e instanceof Error ? e.message : '删除会话失败');
      }
    },
    [abort, mode, ready, stopTypewriter, token]
  );

  const sendUserMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || sending) return;

      stopTypewriter();
      let convId = activeId;
      if (!convId) {
        convId = `${PENDING_PREFIX}${genId()}`;
        const record: ConversationRecord = {
          id: convId,
          title: defaultTitleFromMessage(trimmed),
          messages: [],
          updatedAt: Date.now(),
        };
        persist((prev) => [record, ...prev]);
        setActiveId(convId);
      }

      const conv = conversations.find((c) => c.id === convId);
      const chatIdForApi =
        conv?.backendChatId ??
        (convId.startsWith(PENDING_PREFIX) ? undefined : convId);

      const userMsg: ChatMessage = {
        id: genId(),
        role: 'user',
        content: trimmed,
        createdAt: Date.now(),
      };
      const assistantMsg: ChatMessage = {
        id: genId(),
        role: 'assistant',
        content: '',
        createdAt: Date.now(),
        streaming: true,
      };

      const persistWithMessages = (
        mut: (msgs: ChatMessage[]) => ChatMessage[],
        extra?: Partial<ConversationRecord>
      ) => {
        persist((prev) => {
          const idx = prev.findIndex((c) => c.id === convId);
          if (idx === -1) return prev;
          const c = prev[idx];
          const isFirst = c.messages.length === 0;
          const nextRecord: ConversationRecord = {
            ...c,
            ...extra,
            title: isFirst ? defaultTitleFromMessage(trimmed) : c.title,
            messages: mut(c.messages),
            updatedAt: Date.now(),
          };
          return [
            ...prev.slice(0, idx),
            nextRecord,
            ...prev.slice(idx + 1),
          ];
        });
      };

      const assistantManus: ChatMessage = {
        id: genId(),
        role: 'assistant',
        content: '',
        createdAt: Date.now(),
        streaming: true,
        manusSteps: [],
      };

      if (mode === 'manus') {
        persistWithMessages((m) => [...m, userMsg, assistantManus]);
      } else {
        persistWithMessages((m) => [...m, userMsg, assistantMsg]);
      }

      setSending(true);
      try {
        const result =
          mode === 'manus'
            ? await runManusStream(
                { message: trimmed, chatId: chatIdForApi },
                (p) => {
                  const { route, text } = routeManusSsePayload(p);
                  if (route === 'skip') return;
                  if (route === 'content') {
                    enqueueTypewriter({ convId, messageId: assistantManus.id, delta: text });
                    return;
                  }
                  persist((prev) => {
                    const idx = prev.findIndex((c) => c.id === convId);
                    if (idx === -1) return prev;
                    const c = prev[idx];
                    const nextMessages = c.messages.map((m) => {
                      if (m.id !== assistantManus.id) return m;
                      return {
                        ...m,
                        manusSteps: [...(m.manusSteps ?? []), text],
                      };
                    });
                    const nextRecord: ConversationRecord = {
                      ...c,
                      messages: nextMessages,
                      updatedAt: Date.now(),
                    };
                    return [...prev.slice(0, idx), nextRecord, ...prev.slice(idx + 1)];
                  });
                }
              )
            : await runStream(
                { message: trimmed, chatId: chatIdForApi },
                (chunk) => {
                  enqueueTypewriter({ convId, messageId: assistantMsg.id, delta: chunk });
                },
                'travel'
              );

        flushTypewriter();
        const serverId = result.chatId?.trim();
        if (serverId && serverId.length > 0) {
          persist((prev) => {
            const idx = prev.findIndex((c) => c.id === convId);
            if (idx === -1) return prev;
            const c = prev[idx];
            const nextRecord: ConversationRecord = {
              ...c,
              id: serverId,
              backendChatId: serverId,
              messages: c.messages.map((m) =>
                mode === 'manus' && m.id === assistantManus.id
                  ? finalizeManusAssistantMessage(m)
                  : { ...m, streaming: false }
              ),
              updatedAt: Date.now(),
            };
            const rest = prev.filter((x, i) => i !== idx && x.id !== serverId);
            return sortConversations([nextRecord, ...rest]);
          });
          if (convId !== serverId) {
            setActiveId(serverId);
          }
        } else {
          persist((prev) => {
            const idx = prev.findIndex((c) => c.id === convId);
            if (idx === -1) return prev;
            const c = prev[idx];
            const aid = mode === 'manus' ? assistantManus.id : assistantMsg.id;
            const nextRecord: ConversationRecord = {
              ...c,
              messages: c.messages.map((m) =>
                m.id === aid
                  ? mode === 'manus'
                    ? finalizeManusAssistantMessage(m)
                    : { ...m, streaming: false }
                  : m
              ),
              updatedAt: Date.now(),
            };
            return [
              ...prev.slice(0, idx),
              nextRecord,
              ...prev.slice(idx + 1),
            ];
          });
        }
      } catch (e) {
        const canceled =
          (isAxiosError(e) && e.code === 'ERR_CANCELED') ||
          (e instanceof Error && e.name === 'CanceledError');
        if (canceled) {
          stopTypewriter();
          persist((prev) => {
            const idx = prev.findIndex((c) => c.id === convId);
            if (idx === -1) return prev;
            const c = prev[idx];
            let nextMessages: ChatMessage[];
            if (mode === 'manus') {
              const ui = c.messages.findIndex((m) => m.id === userMsg.id);
              nextMessages = ui >= 0 ? c.messages.slice(0, ui + 1) : c.messages;
            } else {
              nextMessages = c.messages.filter((m) => m.id !== assistantMsg.id);
            }
            const nextRecord: ConversationRecord = {
              ...c,
              messages: nextMessages,
              updatedAt: Date.now(),
            };
            return [
              ...prev.slice(0, idx),
              nextRecord,
              ...prev.slice(idx + 1),
            ];
          });
          return;
        }
        const errText = formatChatError(e);
        stopTypewriter();
        persist((prev) => {
          const idx = prev.findIndex((c) => c.id === convId);
          if (idx === -1) return prev;
          const c = prev[idx];
          if (mode === 'manus') {
            const nextMessages = c.messages.map((m) =>
              m.id === assistantManus.id
                ? { ...m, content: errText, streaming: false }
                : m
            );
            const nextRecord: ConversationRecord = {
              ...c,
              messages: nextMessages,
              updatedAt: Date.now(),
            };
            return [
              ...prev.slice(0, idx),
              nextRecord,
              ...prev.slice(idx + 1),
            ];
          }
          const nextMessages = c.messages.map((m) =>
            m.id === assistantMsg.id
              ? { ...m, content: errText, streaming: false }
              : m
          );
          const nextRecord: ConversationRecord = {
            ...c,
            messages: nextMessages,
            updatedAt: Date.now(),
          };
          return [
            ...prev.slice(0, idx),
            nextRecord,
            ...prev.slice(idx + 1),
          ];
        });
      } finally {
        stopTypewriter();
        setSending(false);
      }
    },
    [
      activeId,
      conversations,
      enqueueTypewriter,
      flushTypewriter,
      mode,
      persist,
      ready,
      runManusStream,
      runStream,
      sending,
      stopTypewriter,
      token,
    ]
  );

  return {
    conversations,
    activeId,
    messages,
    sending,
    listLoading,
    newChat,
    selectChat,
    deleteChat,
    sendUserMessage,
  };
}
