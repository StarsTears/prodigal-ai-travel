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

/** 历史会话 不传 chatId 时返回当前用户全部会话（仅元数据）；传 chatId 时返回该会话及全部消息。 GET /travel/conversations */
export async function conversations(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.conversationsParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultListChatMessageVO>(`/api/travel/conversations`, {
    method: "GET",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}

/** 删除会话 删除指定会话及其消息；需登录 DELETE /travel/conversations */
export async function deleteConversation(
  // 叠加生成的Param类型 (非body参数swagger默认没有生成对象)
  params: API.deleteConversationParams,
  options?: { [key: string]: any }
) {
  return request<API.BaseResultVoid>(`/api/travel/conversations`, {
    method: "DELETE",
    params: {
      ...params,
    },
    ...(options || {}),
  });
}
