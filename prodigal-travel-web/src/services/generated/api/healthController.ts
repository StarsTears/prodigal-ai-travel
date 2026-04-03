// @ts-ignore
/* eslint-disable */
import { request } from '@/request';

/** 此处后端没有提供注释 POST /api/health/check */
export async function check(options?: { [key: string]: any }) {
  return request<string>('/api/health/check', {
    method: 'POST',
    ...(options || {}),
  });
}
