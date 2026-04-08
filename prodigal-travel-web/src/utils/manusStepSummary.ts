/**
 * 当模型未产出综合回复（如达到最大步数）时，根据步骤里的工具原始输出生成可读摘要。
 */

function matchKV(text: string, keys: string[]): string | null {
  for (const k of keys) {
    const re = new RegExp(
      `${k.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')}[：:]\\s*(.+)`,
      'im'
    );
    const m = text.match(re);
    if (m) return m[1].replace(/\s+$/u, '').trim();
  }
  return null;
}

function parseToolBlocks(stepText: string): Array<{ name: string; body: string }> {
  const pattern = /tool name:\s*([^,\n]+?)\s*,\s*tool response:\s*/gi;
  const matches = [...stepText.matchAll(pattern)];
  const blocks: Array<{ name: string; body: string }> = [];
  for (let i = 0; i < matches.length; i++) {
    const m = matches[i];
    const name = m[1].trim();
    const start = (m.index ?? 0) + m[0].length;
    const end =
      i + 1 < matches.length ? (matches[i + 1].index ?? stepText.length) : stepText.length;
    blocks.push({ name, body: stepText.slice(start, end).trim() });
  }
  return blocks;
}

function tryFormatTimeSummary(name: string, body: string): string | null {
  const hint = `${name}\n${body}`;
  if (
    !/Current Time|当前时间|Day of the week|星期|Timezone|时区|getDetailedCurrentTime|getCurrentTime|Time/i.test(
      hint
    )
  ) {
    return null;
  }
  const time = matchKV(body, ['Current Time', '当前时间']);
  const day = matchKV(body, ['Day of the week', '星期']);
  const tz = matchKV(body, ['User Timezone', '用户时区', 'Timezone']);
  const off = matchKV(body, ['UTC Offset', 'UTC 偏移']);
  const parts: string[] = [];
  if (time && day) {
    parts.push(`当前时间为 **${time}**（**${day}**）`);
  } else {
    if (time) parts.push(`当前时间为 **${time}**`);
    if (day) parts.push(`**${day}**`);
  }
  const zone = [tz, off].filter(Boolean).join('，');
  if (zone) parts.push(`时区为 **${zone}**`);
  if (parts.length === 0) return null;
  return `${parts.join('；')}。`;
}

function formatToolBody(body: string): string {
  const t = body.trim();
  if (!t) return '（无返回内容）';
  if (t.startsWith('{') && t.endsWith('}')) {
    try {
      const o = JSON.parse(t) as Record<string, unknown>;
      return Object.entries(o)
        .map(([k, v]) => `- **${k}**：${String(v)}`)
        .join('\n');
    } catch {
      /* fall through */
    }
  }
  if (/:\s*\S/m.test(t)) {
    return t
      .split('\n')
      .map((line) => {
        const s = line.trim();
        if (!s) return '';
        return s.startsWith('-') ? s : `- ${s}`;
      })
      .filter(Boolean)
      .join('\n');
  }
  return t;
}

function isNoiseStep(raw: string): boolean {
  const s = raw.trim();
  if (!s) return true;
  if (/^执行结束:/.test(s)) return true;
  if (/^执行错误/.test(s) || /^错误:/.test(s)) return true;
  if (/^Error running agent/i.test(s)) return true;
  return false;
}

/**
 * 将多步工具日志整理为 Markdown，供「综合回复」展示。
 */
export function summarizeManusSteps(steps: string[]): string {
  const useful = steps.filter((s) => !isNoiseStep(s));
  const endNotice = steps.find((s) => /^执行结束:/.test(s.trim()))?.trim();

  if (useful.length === 0) {
    if (endNotice) {
      return `本轮未拿到可用的自然语言收尾（${endNotice}）。请展开上方「思考与执行过程」查看工具原始输出。`;
    }
    return '暂无工具结果可整理为摘要。';
  }

  const out: string[] = ['**摘要**（根据上方工具返回整理）：', ''];
  for (const step of useful) {
    const tools = parseToolBlocks(step);
    if (tools.length > 0) {
      for (const { name, body } of tools) {
        const lineSummary = tryFormatTimeSummary(name, body);
        out.push(`### ${name}`);
        out.push('');
        out.push(lineSummary ?? formatToolBody(body));
        out.push('');
      }
    } else {
      out.push(step.trim());
      out.push('');
    }
  }
  if (endNotice) {
    out.push(`> 说明：${endNotice}`);
  }
  return out.join('\n').trim();
}
