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
  if (!t) return '';
  if (t.startsWith('{') && t.endsWith('}')) {
    try {
      const o = JSON.parse(t) as Record<string, unknown>;
      const lines = Object.entries(o)
        .filter(([, v]) => String(v).trim())
        .map(([k, v]) => `- **${k}**：${String(v)}`)
        .join('\n');
      return lines;
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

function isMeaningfulBody(body: string): boolean {
  const t = body.trim();
  if (!t) return false;
  if (t === '{}' || t === '[]' || t === 'null' || t === 'undefined') return false;
  return true;
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
  const toolSections: Array<{ name: string; text: string }> = [];
  const freeTextSections: string[] = [];

  for (const step of useful) {
    const tools = parseToolBlocks(step);
    if (tools.length > 0) {
      for (const { name, body } of tools) {
        if (!isMeaningfulBody(body)) continue;
        const lineSummary = tryFormatTimeSummary(name, body);
        const text = (lineSummary ?? formatToolBody(body)).trim();
        if (!text) continue;
        toolSections.push({ name, text });
      }
      continue;
    }
    const text = step.trim();
    if (text) freeTextSections.push(text);
  }

  // 若步骤都没有正文，就不输出综合回复内容。
  if (toolSections.length === 0 && freeTextSections.length === 0) {
    return '';
  }

  const out: string[] = [
    '## 综合回复',
    '已为你提炼关键信息，给你一版可直接参考的结果：',
    '',
  ];
  for (const { name, text } of toolSections) {
    out.push(`### ${name}`);
    out.push('');
    out.push(text);
    out.push('');
  }
  for (const text of freeTextSections) {
    out.push(text);
    out.push('');
  }

  out.push('如果你愿意，我可以基于这版结果继续帮你细化下一步建议。');
  return out.join('\n').trim();
}
