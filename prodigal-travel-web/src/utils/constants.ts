const readApiBase = (): string => {
  if (typeof process !== 'undefined' && process.env?.UMI_APP_API_BASE) {
    return String(process.env.UMI_APP_API_BASE).replace(/\/$/, '');
  }
  return '';
};

/**
 * 后端 API 根路径；请求路径仍以 `/api/...` 开头（与 Spring `context-path=/api` 一致）。
 * 开发环境可留空走 `config/config.ts` 的 proxy；部署时设 `UMI_APP_API_BASE`。
 * 已登录时由 `request.ts` 注入 `Authorization: Bearer`；`X-User-Id` 仍为兼容占位，可用 `UMI_APP_USER_ID` 覆盖。
 */
export const API_BASE: string = readApiBase();

export const CONVERSATIONS_STORAGE_KEY = 'ai-travel-conversations-v1';

export const AUTH_TOKEN_KEY = 'prodigal-travel-auth-token';
export const AUTH_USER_KEY = 'prodigal-travel-auth-user';
