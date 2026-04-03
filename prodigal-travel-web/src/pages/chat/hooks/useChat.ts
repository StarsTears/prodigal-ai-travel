import { isAxiosError } from 'axios';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { CONVERSATIONS_STORAGE_KEY } from '@/utils/constants';
import type { ChatMessage, ConversationRecord } from '@/types';
import { useStreaming } from './useStreaming';

const genId = (): string => {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `id-${Date.now()}-${Math.random().toString(16).slice(2)}`;
};

const readStore = (): ConversationRecord[] => {
  if (typeof window === 'undefined') return [];
  try {
    const raw = window.localStorage.getItem(CONVERSATIONS_STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    return parsed.filter(isConversationRecord);
  } catch {
    return [];
  }
};

const isConversationRecord = (v: unknown): v is ConversationRecord => {
  if (typeof v !== 'object' || v === null) return false;
  const o = v as ConversationRecord;
  return (
    typeof o.id === 'string' &&
    typeof o.title === 'string' &&
    Array.isArray(o.messages) &&
    typeof o.updatedAt === 'number' &&
    (o.backendChatId === undefined || typeof o.backendChatId === 'string')
  );
};

const writeStore = (list: ConversationRecord[]) => {
  window.localStorage.setItem(
    CONVERSATIONS_STORAGE_KEY,
    JSON.stringify(list)
  );
};

const defaultTitleFromMessage = (text: string): string => {
  const t = text.replace(/\s+/g, ' ').trim();
  if (!t) return '新对话';
  return t.length > 24 ? `${t.slice(0, 24)}…` : t;
};

const sortConversations = (
  list: ConversationRecord[]
): ConversationRecord[] =>
  [...list].sort((a, b) => b.updatedAt - a.updatedAt);

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
  const { runStream, abort } = useStreaming();
  const [conversations, setConversations] = useState<ConversationRecord[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [historySearch, setHistorySearch] = useState('');
  const [sending, setSending] = useState(false);

  useEffect(() => {
    const list = readStore();
    setConversations(list);
    if (list.length > 0) {
      const latest = sortConversations(list)[0];
      setActiveId(latest.id);
    }
  }, []);

  const persist = useCallback(
    (updater: (prev: ConversationRecord[]) => ConversationRecord[]) => {
      setConversations((prev) => {
        const next = sortConversations(updater(prev));
        writeStore(next);
        return next;
      });
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
    const id = genId();
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
    },
    [abort]
  );

  const deleteChat = useCallback(
    (id: string) => {
      abort();
      let nextList: ConversationRecord[] = [];
      setConversations((prev) => {
        nextList = prev.filter((c) => c.id !== id);
        writeStore(nextList);
        return nextList;
      });
      setActiveId((cur) => {
        if (cur !== id) return cur;
        return sortConversations(nextList)[0]?.id ?? null;
      });
    },
    [abort]
  );

  const sendUserMessage = useCallback(
    async (text: string) => {
      const trimmed = text.trim();
      if (!trimmed || sending) return;

      let convId = activeId;
      if (!convId) {
        convId = genId();
        const record: ConversationRecord = {
          id: convId,
          title: defaultTitleFromMessage(trimmed),
          messages: [],
          updatedAt: Date.now(),
        };
        persist((prev) => [record, ...prev]);
        setActiveId(convId);
      }

      const backendChatId = conversations.find((c) => c.id === convId)
        ?.backendChatId;

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
          { message: trimmed, chatId: backendChatId },
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

        persist((prev) => {
          const idx = prev.findIndex((c) => c.id === convId);
          if (idx === -1) return prev;
          const c = prev[idx];
          const nextRecord: ConversationRecord = {
            ...c,
            backendChatId: result.chatId,
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
    [activeId, conversations, persist, runStream, sending]
  );

  const filteredConversations = useMemo(() => {
    const q = historySearch.trim().toLowerCase();
    if (!q) return conversations;
    return conversations.filter(
      (c) =>
        c.title.toLowerCase().includes(q) ||
        c.messages.some((m) => m.content.toLowerCase().includes(q))
    );
  }, [conversations, historySearch]);

  return {
    conversations: filteredConversations,
    activeId,
    messages,
    historySearch,
    setHistorySearch,
    sending,
    newChat,
    selectChat,
    deleteChat,
    sendUserMessage,
  };
}
