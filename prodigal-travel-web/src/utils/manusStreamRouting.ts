import type { ManusSsePayload } from '@/services/travelChat';

export type ManusChunkRoute = 'skip' | 'step' | 'content';

const NOISE_EXACT = new Set([
  'Think complete!No need to act',
  'No tools need to be called.',
]);

/**
 * 后端已改为结构化 SSE 命名事件：
 * - `event: delta` -> 最终答复，进入综合回复
 * - `event: step`  -> 思考/工具日志，进入「思考与执行过程」折叠面板
 * - 无事件名的 `message` -> 旧格式兼容，仍按文本前缀猜测分类
 *
 * 旧实现所有分片都走 routeManusPlainChunk，导致以普通文字开头的步骤日志
 * 被当成最终答复展示；现在事件名本身就决定了去处。
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
  if (p.kind === 'step') {
    const trimmed = p.text.trim();
    if (!trimmed || NOISE_EXACT.has(trimmed)) {
      return { route: 'skip', text: '' };
    }
    return { route: 'step', text: p.text };
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
