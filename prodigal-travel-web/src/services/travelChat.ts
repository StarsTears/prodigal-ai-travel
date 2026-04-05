import { chat as postTravelChatRequest } from '@/api/travelAssistant';
import type { BaseResult, TravelChatResponse } from '@/types';

/**
 * 聊天请求封装在 `@umijs/openapi` 生成的 `generated/api/travelAssistant.ts`（`npm run openapi2ts`）。
 * 路径前缀与 Spring `context-path=/api` 一致；重新生成后若路径异常，可将 `.openapi2tsrc.json` 中
 * `apiPrefix` 设为 `"'/api'"`（带引号）再执行 openapi2ts。
 */
export const TRAVEL_CHAT_POST_PATH = '/api/travel/chat';

export interface TravelChatCallOptions {
  message: string;
  chatId?: string | null;
  signal?: AbortSignal;
}

function unwrapBaseResult(
  payload: BaseResult<TravelChatResponse> | undefined
): TravelChatResponse {
  if (!payload) {
    throw new Error('响应体为空');
  }
  if (!payload.status || payload.code !== 0 || payload.data == null) {
    const msg =
      typeof payload.msg === 'string' && payload.msg.trim()
        ? payload.msg
        : '请求失败';
    throw new Error(msg);
  }
  const d = payload.data;
  return {
    chatId: d.chatId ?? '',
    answer: d.answer ?? '',
  };
}

/**
 * 旅游助手对话：调用 openapi 生成的 `chat`，并解析 `BaseResult<TravelChatResponse>`。
 */
export async function postTravelChat(
  options: TravelChatCallOptions
): Promise<TravelChatResponse> {
  const res = await postTravelChatRequest(
    {
      message: options.message,
      ...(options.chatId ? { chatId: options.chatId } : {}),
    },
    { signal: options.signal }
  );
  return unwrapBaseResult(res.data as BaseResult<TravelChatResponse>);
}

/**
 * 后端当前为整段 JSON 返回。此前用 8 字 + 16ms 循环「假流式」，长回复会额外多等数秒且首字仍要等模型结束。
 * 收到完整答案后一次性写入 UI，避免人为延迟；真正流式需后端 SSE/WebFlux。
 */
export async function streamTravelAnswer(
  options: TravelChatCallOptions,
  onDelta: (chunk: string) => void
): Promise<TravelChatResponse> {
  const data = await postTravelChat(options);
  const text = data.answer ?? '';
  if (text.length > 0 && !options.signal?.aborted) {
    onDelta(text);
  }
  return data;
}
