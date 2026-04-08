export const CHAT_MODE_STORAGE_KEY = 'prodigal_chat_mode';

export type ChatMode = 'travel' | 'manus';

export function persistChatMode(mode: ChatMode): void {
  if (typeof sessionStorage === 'undefined') return;
  sessionStorage.setItem(CHAT_MODE_STORAGE_KEY, mode);
}
