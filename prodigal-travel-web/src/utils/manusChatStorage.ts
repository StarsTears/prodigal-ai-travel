import type { ConversationRecord } from '@/types';

const STORAGE_KEY = 'prodigal-manus-conversations-v1';

export interface ManusPersistedState {
  conversations: ConversationRecord[];
  activeId: string | null;
}

export function loadManusPersistedState(): ManusPersistedState {
  if (typeof localStorage === 'undefined') {
    return { conversations: [], activeId: null };
  }
  try {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return { conversations: [], activeId: null };
    const o = JSON.parse(raw) as Partial<ManusPersistedState>;
    const list = Array.isArray(o.conversations) ? o.conversations : [];
    const active =
      typeof o.activeId === 'string' && list.some((c) => c.id === o.activeId)
        ? o.activeId
        : list[0]?.id ?? null;
    return { conversations: list, activeId: active };
  } catch {
    return { conversations: [], activeId: null };
  }
}

export function saveManusPersistedState(state: ManusPersistedState): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  } catch {
    /* quota / private mode */
  }
}

export function clearManusPersistedState(): void {
  if (typeof localStorage === 'undefined') return;
  try {
    localStorage.removeItem(STORAGE_KEY);
  } catch {
    /* ignore */
  }
}
