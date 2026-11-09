import { AUTH_TOKEN_KEY, AUTH_USER_KEY } from '@/utils/constants';
import { writeDisplayName } from '@/utils/preferences';
import { clearManusPersistedState } from '@/utils/manusChatStorage';
import { CHAT_MODE_STORAGE_KEY } from '@/utils/chatMode';

export const AUTH_EXPIRED_EVENT = 'prodigal-auth-expired';

export function getStoredToken(): string | null {
  if (typeof window === 'undefined') return null;
  try {
    const t = window.localStorage.getItem(AUTH_TOKEN_KEY);
    return t?.trim() ? t.trim() : null;
  } catch {
    return null;
  }
}

export function getStoredUser(): API.LoginResponse | null {
  if (typeof window === 'undefined') return null;
  try {
    const raw = window.localStorage.getItem(AUTH_USER_KEY);
    if (!raw) return null;
    const o = JSON.parse(raw) as unknown;
    if (typeof o !== 'object' || o === null) return null;
    return o as API.LoginResponse;
  } catch {
    return null;
  }
}

export function setAuthSession(token: string, user: API.LoginResponse): void {
  try {
    window.localStorage.setItem(AUTH_TOKEN_KEY, token);
    window.localStorage.setItem(AUTH_USER_KEY, JSON.stringify(user));
  } catch {
    /* ignore */
  }
}

export function clearAuthSession(): void {
  try {
    window.localStorage.removeItem(AUTH_TOKEN_KEY);
    window.localStorage.removeItem(AUTH_USER_KEY);
  } catch {
    /* ignore */
  }
}

/**
 * 退出登录 / 注销时清空当前用户的全部本机缓存。
 * 涵盖：登录态、展示名（含自定义标记）、超级智能体会话、聊天模式。
 * 注意：定位开关与地理状态属于设备级偏好，不在此清理。
 */
export function clearUserLocalCache(): void {
  clearAuthSession();
  writeDisplayName('');
  clearManusPersistedState();
  try {
    window.sessionStorage.removeItem(CHAT_MODE_STORAGE_KEY);
  } catch {
    /* ignore */
  }
}

export function dispatchAuthExpired(): void {
  window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
}
