import { chat as postTravelChatRequest } from '@/services/generated/api/lvyouzhushou';
import type { BaseResult, TravelChatResponse } from '@/types';

/**
 * 聊天请求封装在 `@umijs/openapi` 生成的 `generated/api/lvyouzhushou.ts`（`npm run openapi2ts`）。
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

const CHUNK_SIZE = 8;
const CHUNK_DELAY_MS = 16;

export async function streamTravelAnswer(
  options: TravelChatCallOptions,
  onDelta: (chunk: string) => void
): Promise<TravelChatResponse> {
  const data = await postTravelChat(options);
  const text = data.answer ?? '';
  for (
    let i = 0;
    i < text.length && !options.signal?.aborted;
    i += CHUNK_SIZE
  ) {
    onDelta(text.slice(i, i + CHUNK_SIZE));
    await new Promise<void>((r) => {
      setTimeout(r, CHUNK_DELAY_MS);
    });
  }
  return data;
}
