import { API_BASE } from '@/utils/constants';
import { getStoredToken } from '@/utils/authStorage';
import type { TravelChatResponse } from '@/types';

export const TRAVEL_CHAT_SSE_PATH = '/api/travel/chat/sse_emitter';
export const MANUS_CHAT_SSE_PATH = '/api/travel/manus/chat';

export interface StreamCallOptions {
  message: string;
  chatId?: string | null;
  signal?: AbortSignal;
}

type StreamKind = 'travel' | 'manus';

function buildApiUrl(path: string): string {
  return `${API_BASE || ''}${path}`;
}

function resolveChatId(options: StreamCallOptions): string {
  const id = options.chatId?.trim();
  if (id) return id;
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID();
  }
  return `chat-${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function parseEventPayload(raw: string): string {
  const text = raw.trim();
  if (!text || text === '[DONE]') return '';
  if (text.startsWith('{') && text.endsWith('}')) {
    try {
      const obj = JSON.parse(text) as { data?: unknown; answer?: unknown; delta?: unknown };
      if (typeof obj.delta === 'string') return obj.delta;
      if (typeof obj.answer === 'string') return obj.answer;
      if (typeof obj.data === 'string') return obj.data;
    } catch {
      // Fall through and return raw text.
    }
  }
  return text;
}

async function streamByFetch(
  kind: StreamKind,
  options: StreamCallOptions,
  onDelta: (chunk: string) => void
): Promise<TravelChatResponse> {
  const chatId = resolveChatId(options);
  const token = getStoredToken();
  const path = kind === 'manus' ? MANUS_CHAT_SSE_PATH : TRAVEL_CHAT_SSE_PATH;
  const res = await fetch(buildApiUrl(path), {
    method: 'POST',
    headers: {
      Accept: 'text/event-stream',
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: JSON.stringify({ message: options.message, chatId }),
    signal: options.signal,
  });
  if (!res.ok) {
    const errText = await res.text().catch(() => '');
    throw new Error(errText || `请求失败 (${res.status})`);
  }
  if (!res.body) {
    throw new Error('流式响应体为空');
  }

  const decoder = new TextDecoder();
  const reader = res.body.getReader();
  let buffer = '';
  let answer = '';
  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    let sep = buffer.indexOf('\n\n');
    while (sep >= 0) {
      const eventBlock = buffer.slice(0, sep);
      buffer = buffer.slice(sep + 2);
      const dataLines = eventBlock
        .split('\n')
        .map((line) => line.trim())
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.slice(5).trim());
      if (dataLines.length > 0) {
        const chunk = parseEventPayload(dataLines.join('\n'));
        if (chunk) {
          answer += chunk;
          onDelta(chunk);
        }
      }
      sep = buffer.indexOf('\n\n');
    }
  }
  const tail = parseEventPayload(buffer.replace(/^data:\s*/gm, '').trim());
  if (tail) {
    answer += tail;
    onDelta(tail);
  }
  return { chatId, answer };
}

export async function streamTravelAnswer(
  options: StreamCallOptions,
  onDelta: (chunk: string) => void
): Promise<TravelChatResponse> {
  return streamByFetch('travel', options, onDelta);
}

export async function streamManusAnswer(
  options: StreamCallOptions,
  onDelta: (chunk: string) => void
): Promise<TravelChatResponse> {
  return streamByFetch('manus', options, onDelta);
}
