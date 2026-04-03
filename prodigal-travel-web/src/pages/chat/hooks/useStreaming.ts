import { useCallback, useRef } from 'react';
import { streamTravelAnswer } from '@/services/travelChat';
import type { TravelChatResponse } from '@/types';

/**
 * 按接口约定仅传「当前用户句」+ 可选 `chatId`；历史上下文由后端记忆顾问维护。
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
      onDelta: (chunk: string) => void
    ): Promise<TravelChatResponse> => {
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

  return { runStream, abort };
}
