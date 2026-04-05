import { message } from 'antd';
import { isAxiosError } from 'axios';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  conversations as fetchConversations,
  deleteConversationByChatId as deleteConversationApi,
} from '@/api/travelAssistant';
import { useAuth } from '@/contexts/AuthContext';
import type { ChatMessage, ConversationRecord } from '@/types';
import { assertBaseResultOk, unwrapBaseResult } from '@/utils/apiResult';
import { useStreaming } from './useStreaming';

const genId = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const PENDING_PREFIX = 'pending-';

const defaultTitleFromMessage = (text: string): string => {
  const t = text.replace(/\s+/g, ' ').trim();
  if (!t) return '新对话';
  return t.length > 24 ? `${t.slice(0, 24)}…` : t;
};

const sortConversations = (
  list: ConversationRecord[]
): ConversationRecord[] =>
  [...list].sort((a, b) => b.updatedAt - a.updatedAt);

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
  return {
    id: cid,
    backendChatId: cid,
    title: (vo.title ?? '').trim() || '对话',
    messages: [],
    updatedAt: Date.now() - orderIndex,
  };
}

/** 详情接口：含完整消息 */
function voToDetailRecord(vo: API.ChatMessageVO): ConversationRecord {
  const cid = vo.conversationId ?? '';
  return {
    id: cid,
    backendChatId: cid,
    title: (vo.title ?? '').trim() || '对话',
    messages: mapApiMessages(vo.messages),
    updatedAt: Date.now(),
  };
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

export function useChat() {
  const { token, ready } = useAuth();
  const { runStream, abort } = useStreaming();
  const [conversations, setConversations] = useState<ConversationRecord[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const [listLoading, setListLoading] = useState(false);
  const activeIdRef = useRef<string | null>(null);

  useEffect(() => {
    activeIdRef.current = activeId;
  }, [activeId]);

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
    [mergeDetailIntoList, ready, token]
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
   * 须等 `ready`（本地 token 与用户态已恢复完毕）且存在 token 才拉会话历史，
   * 避免首屏未登录或 hydration 完成前就请求 `/travel/conversations`。
   */
  useEffect(() => {
    if (!ready) {
      return;
    }
    if (!token) {
      setConversations([]);
      setActiveId(null);
      setListLoading(false);
      return;
    }
    void refreshListRef.current();
  }, [token, ready]);

  const persist = useCallback(
    (updater: (prev: ConversationRecord[]) => ConversationRecord[]) => {
      setConversations((prev) => sortConversations(updater(prev)));
    },
    []
  );

  const activeConversation = useMemo(() => {
    if (!activeId) return null;
    return conversations.find((c) => c.id === activeId) ?? null;
  }, [activeId, conversations]);

  const messages = activeConversation?.messages ?? [];

  const newChat = useCallback(() => {
    abort();
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
      setActiveId(id);
      if (ready && token && !id.startsWith(PENDING_PREFIX)) {
        void loadConversationDetail(id);
      }
    },
    [abort, loadConversationDetail, ready, token]
  );

  const deleteChat = useCallback(
    async (id: string) => {
      abort();
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
    [abort, ready, token]
  );

  const sendUserMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || sending) return;

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

      persistWithMessages((m) => [...m, userMsg, assistantMsg]);

      setSending(true);
      try {
        const result = await runStream(
          { message: trimmed, chatId: chatIdForApi },
          (chunk) => {
            persist((prev) => {
              const idx = prev.findIndex((c) => c.id === convId);
              if (idx === -1) return prev;
              const c = prev[idx];
              const nextMessages = c.messages.map((m) =>
                m.id === assistantMsg.id
                  ? { ...m, content: m.content + chunk }
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
          }
        );

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
                m.id === assistantMsg.id ? { ...m, streaming: false } : m
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
            const nextRecord: ConversationRecord = {
              ...c,
              messages: c.messages.map((m) =>
                m.id === assistantMsg.id ? { ...m, streaming: false } : m
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
          persist((prev) => {
            const idx = prev.findIndex((c) => c.id === convId);
            if (idx === -1) return prev;
            const c = prev[idx];
            const nextRecord: ConversationRecord = {
              ...c,
              messages: c.messages.filter((m) => m.id !== assistantMsg.id),
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
        persist((prev) => {
          const idx = prev.findIndex((c) => c.id === convId);
          if (idx === -1) return prev;
          const c = prev[idx];
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
        setSending(false);
      }
    },
    [activeId, conversations, persist, ready, runStream, sending, token]
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
