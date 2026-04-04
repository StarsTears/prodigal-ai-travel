// @ts-ignore
/* eslint-disable */
import { request } from "@/request";

/** 旅游助手对话 需登录：请求头 Authorization: Bearer {token}。可传 departureCity、tripDays、tripFocus；结合 RAG 与工具调用。 POST /travel/chat */
export async function chat(
  body: API.ChatRequest,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultTravelChatResponse>(`/api/travel/chat`, {
    method: "POST",
    headers: {
      "Content-Type": "application/json",
    },
    data: body,
    ...(options || {}),
  });
}
