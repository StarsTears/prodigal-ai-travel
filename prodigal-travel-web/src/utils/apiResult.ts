/** 解析与后端 `BaseResult<T>` 一致的 axios 响应体 */
export function unwrapBaseResult<T>(d: {
  code?: number;
  status?: boolean;
  msg?: string;
  data?: T | null;
} | undefined): T {
  if (d == null || d.code !== 0 || d.status !== true || d.data == null) {
    const m =
      typeof d?.msg === 'string' && d.msg.trim() ? d.msg.trim() : '请求失败';
    throw new Error(m);
  }
  return d.data;
}

/** `BaseResult<Void>` 等 `data` 可为 null 的成功响应 */
export function assertBaseResultOk(d: {
  code?: number;
  status?: boolean;
  msg?: string;
  data?: unknown;
} | undefined): void {
  if (d == null || d.code !== 0 || d.status !== true) {
    const m =
      typeof d?.msg === 'string' && d.msg.trim() ? d.msg.trim() : '请求失败';
    throw new Error(m);
  }
}
