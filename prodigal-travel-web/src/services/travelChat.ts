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

export type ManusSsePayload =
  | { kind: 'delta'; text: string }
  | { kind: 'step'; text: string };

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

/** 解析已完整的 SSE 事件块（以空行分隔），返回未消费完的尾部 buffer */
function parseCompleteSseBlocks(buffer: string): {
  consumed: Array<{ eventName: string; data: string }>;
  rest: string;
} {
  const consumed: Array<{ eventName: string; data: string }> = [];
  // Spring / 反向代理常见 CRLF；只认 \n\n 会导致整段流无法拆包，onPayload 从不触发、界面一直转圈
  let rest = buffer.replace(/\r\n/g, '\n');
  while (true) {
    const sep = rest.indexOf('\n\n');
    if (sep < 0) break;
    const block = rest.slice(0, sep);
    rest = rest.slice(sep + 2);
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const rawLine of block.split('\n')) {
      const line = rawLine.replace(/\r$/, '');
      if (!line) continue;
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        const v = line.slice(5);
        dataLines.push(v.startsWith(' ') ? v.slice(1) : v);
      }
    }
    const data = dataLines.join('\n');
    if (data !== '' && data !== '[DONE]') {
      consumed.push({ eventName, data });
    }
  }
  return { consumed, rest };
}

function mapManusEvent(eventName: string, data: string): ManusSsePayload | null {
  const text = parseEventPayload(data);
  if (!text) return null;
  if (eventName === 'delta') {
    return { kind: 'delta', text };
  }
  if (eventName === 'step') {
    return { kind: 'step', text };
  }
  // Spring `SseEmitter.send(String)` 等：无 event 名时视为 message，由前端 `routeManusSsePayload` 再分类
  return { kind: 'step', text };
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
    const { consumed, rest } = parseCompleteSseBlocks(buffer);
    buffer = rest;
    for (const { eventName, data } of consumed) {
      const chunk = parseEventPayload(data);
      if (!chunk) continue;
      answer += chunk;
      onDelta(chunk);
    }
  }
  const { consumed: tailEvents, rest: tailRest } = parseCompleteSseBlocks(buffer + '\n\n');
  buffer = tailRest;
  for (const { eventName, data } of tailEvents) {
    const chunk = parseEventPayload(data);
    if (!chunk) continue;
    answer += chunk;
    onDelta(chunk);
  }
  const tail = parseEventPayload(buffer.replace(/^data:\s*/gm, '').trim());
  if (tail) {
    answer += tail;
    onDelta(tail);
  }
  return { chatId, answer };
}

async function streamManusByFetch(
  options: StreamCallOptions,
  onPayload: (p: ManusSsePayload) => void
): Promise<TravelChatResponse> {
  const chatId = resolveChatId(options);
  const token = getStoredToken();
  const res = await fetch(buildApiUrl(MANUS_CHAT_SSE_PATH), {
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
    const { consumed, rest } = parseCompleteSseBlocks(buffer);
    buffer = rest;
    for (const { eventName, data } of consumed) {
      const mapped = mapManusEvent(eventName, data);
      if (!mapped) continue;
      answer += mapped.text;
      onPayload(mapped);
    }
  }
  // 与 streamByFetch 一致：用空行冲刷「最后一个未以 \n\n 结尾」的事件，否则后端已 complete 前端仍拿不到数据
  const { consumed: tailEvents, rest: tailBuf } = parseCompleteSseBlocks(`${buffer}\n\n`);
  buffer = tailBuf;
  for (const { eventName, data } of tailEvents) {
    const mapped = mapManusEvent(eventName, data);
    if (!mapped) continue;
    answer += mapped.text;
    onPayload(mapped);
  }
  // 仍非标准 SSE 的裸文本（极少数容器行为）
  const orphan = buffer.replace(/\r\n/g, '\n').trim();
  if (orphan) {
    const mapped = mapManusEvent('message', orphan);
    if (mapped) {
      answer += mapped.text;
      onPayload(mapped);
    }
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
  onPayload: (p: ManusSsePayload) => void
): Promise<TravelChatResponse> {
  return streamManusByFetch(options, onPayload);
}
