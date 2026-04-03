import axios, { type AxiosInstance } from 'axios';
import { API_BASE } from '@/utils/constants';

const readUserId = (): string => {
  if (typeof process !== 'undefined' && process.env?.UMI_APP_USER_ID) {
    return String(process.env.UMI_APP_USER_ID);
  }
  return 'guest';
};

/**
 * 统一 axios 实例：开发环境 baseURL 为空时走相对路径 + Umi proxy。
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
  return config;
});
