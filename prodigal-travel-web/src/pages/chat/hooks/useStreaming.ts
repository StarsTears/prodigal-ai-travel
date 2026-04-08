import { useCallback, useRef } from 'react';
import {
  streamManusAnswer,
  streamTravelAnswer,
  type ManusSsePayload,
} from '@/services/travelChat';
import type { TravelChatResponse } from '@/types';

export type StreamChatKind = 'travel' | 'manus';

export type { ManusSsePayload };

/**
 * 旅游助手：MCP 文本流。
 * 超级智能体：后端多为默认 SSE `message` + 整段字符串（每步一次）；前端再按正文区分工具日志与综合回复。
 */
export function useStreaming() {
  const abortRef = useRef<AbortController | null>(null);

  const abort = useCallback(() => {
    abortRef.current?.abort();
    abortRef.current = null;
  }, []);

  const runStream = useCallback(
    async (
      args: { message: string; chatId?: string | null },
      onDelta: (chunk: string) => void,
      kind: StreamChatKind = 'travel'
    ): Promise<TravelChatResponse> => {
      if (kind === 'manus') {
        throw new Error('use runManusStream for manus mode');
      }
      abort();
      const controller = new AbortController();
      abortRef.current = controller;
      try {
        return await streamTravelAnswer(
          {
            message: args.message,
            chatId: args.chatId ?? undefined,
            signal: controller.signal,
          },
          onDelta
        );
      } finally {
        abortRef.current = null;
      }
    },
    [abort]
  );

  const runManusStream = useCallback(
    async (
      args: { message: string; chatId?: string | null },
      onPayload: (p: ManusSsePayload) => void
    ): Promise<TravelChatResponse> => {
      abort();
      const controller = new AbortController();
      abortRef.current = controller;
      try {
        return await streamManusAnswer(
          {
            message: args.message,
            chatId: args.chatId ?? undefined,
            signal: controller.signal,
          },
          onPayload
        );
      } finally {
        abortRef.current = null;
      }
    },
    [abort]
  );

  return { runStream, runManusStream, abort };
}
