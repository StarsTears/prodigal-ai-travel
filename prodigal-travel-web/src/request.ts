import axios, { type AxiosInstance } from 'axios';
import { API_BASE } from '@/utils/constants';
import {
  AUTH_EXPIRED_EVENT,
  clearAuthSession,
  getStoredToken,
} from '@/utils/authStorage';

const readUserId = (): string => {
  if (typeof process !== 'undefined' && process.env?.UMI_APP_USER_ID) {
    return String(process.env.UMI_APP_USER_ID);
  }
  return 'guest';
};

/**
 * 统一 axios 实例：开发环境 baseURL 为空时走相对路径 + Umi proxy。
 * 已登录时在请求头附加 `Authorization: Bearer <token>`（与后端 JWT 过滤器一致）。
 */
export const request: AxiosInstance = axios.create({
  baseURL: API_BASE || undefined,
  timeout: 180_000,
  headers: {
    'Content-Type': 'application/json',
  },
});

request.interceptors.request.use((config) => {
  config.headers.set('X-User-Id', readUserId());
  const token = getStoredToken();
  if (token) {
    config.headers.set('Authorization', `Bearer ${token}`);
  } else {
    config.headers.delete('Authorization');
  }
  return config;
});

request.interceptors.response.use(
  (res) => res,
  (err: unknown) => {
    if (!axios.isAxiosError(err) || err.response?.status !== 401) {
      return Promise.reject(err);
    }
    const url = String(err.config?.url ?? '');
    if (url.includes('/travel/')) {
      clearAuthSession();
      if (typeof window !== 'undefined') {
        window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
      }
    }
    return Promise.reject(err);
  }
);
