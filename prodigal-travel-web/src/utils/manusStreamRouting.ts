import type { ManusSsePayload } from '@/services/travelChat';

export type ManusChunkRoute = 'skip' | 'step' | 'content';

const NOISE_EXACT = new Set([
  'Think complete!No need to act',
  'No tools need to be called.',
]);

/**
 * 与当前后端对齐：`/travel/manus/chat` 多为默认 `message` 事件 + 整段字符串；
 * `act()` 产出以 `tool name:` 开头；最终模型回复为普通正文，应进入综合回复而非折叠步骤。
 */
export function routeManusSsePayload(p: ManusSsePayload): {
  route: ManusChunkRoute;
  text: string;
} {
  if (p.kind === 'delta') {
    return p.text.trim()
      ? { route: 'content', text: p.text }
      : { route: 'skip', text: '' };
  }
  return routeManusPlainChunk(p.text);
}

export function routeManusPlainChunk(raw: string): {
  route: ManusChunkRoute;
  text: string;
} {
  const trimmed = raw.trim();
  if (!trimmed) return { route: 'skip', text: '' };
  if (NOISE_EXACT.has(trimmed)) return { route: 'skip', text: '' };
  if (
    trimmed.startsWith('执行错误') ||
    trimmed.startsWith('执行结束:') ||
    trimmed.startsWith('错误:') ||
    trimmed.startsWith('Error running agent')
  ) {
    return { route: 'step', text: raw };
  }
  if (trimmed.startsWith('tool name:')) {
    return { route: 'step', text: raw };
  }
  return { route: 'content', text: raw };
}
