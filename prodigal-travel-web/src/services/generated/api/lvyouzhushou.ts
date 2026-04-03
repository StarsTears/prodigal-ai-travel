// @ts-ignore
/* eslint-disable */
import { request } from '@/request';

/** 贵州旅游助手对话 可传 departureCity、tripDays、tripFocus 辅助智能行程规划；结合贵州省 RAG 与工具调用 POST /api/travel/chat */
export async function chat(
  body: API.ChatRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultTravelChatResponse>('/api/travel/chat', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    data: body,
    ...(options || {}),
  });
}
