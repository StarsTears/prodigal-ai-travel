/**
 * 时间展示工具：聊天气泡始终展示完整「年月日 + 周几 + 时分」，
 * 如 2026年4月28日 周三 12:25。侧边栏会话只保留标题，不展示时间。
 */

const WEEKDAYS = ['日', '一', '二', '三', '四', '五', '六'];

function pad(n: number): string {
  return n < 10 ? `0${n}` : `${n}`;
}

function weekdayOf(d: Date): string {
  return `周${WEEKDAYS[d.getDay()]}`;
}

function hm(d: Date): string {
  return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
}

/**
 * 聊天气泡时间：始终展示完整「年月日 + 周几 + 时分」。
 * 例：2026年4月28日 周三 12:25
 */
export function formatMessageTime(ts: number): string {
  if (!Number.isFinite(ts) || ts <= 0) return '';
  const d = new Date(ts);
  const datePart = `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`;
  return `${datePart} ${weekdayOf(d)} ${hm(d)}`;
}
