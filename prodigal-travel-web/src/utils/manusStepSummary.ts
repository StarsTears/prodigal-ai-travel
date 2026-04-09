/**
 * 当模型未产出综合回复（如达到最大步数）时，根据步骤里的工具原始输出生成可读摘要。
 */

/** 不在「综合回复」中展示的内部控制类工具（方法名小写匹配）。 */
const INTERNAL_TOOL_NAMES = new Set(['doterminate']);

const TOOL_SECTION_TITLE: Record<string, string> = {
  searchWeb: '网络检索',
  recommendAttractions: '景点推荐',
  searchImage: '图片检索',
  sendEmail: '邮件发送',
  sendEmailWithAttachment: '邮件发送（附件）',
  sendEmailWithImageUrls: '邮件发送（图片）',
  generatePDF: 'PDF 生成',
  writeFile: '写入文件',
  readFile: '读取文件',
};

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

function unwrapToolResponseBody(raw: string): string {
  const t = raw.trim();
  if (
    (t.startsWith('"') && t.endsWith('"')) ||
    (t.startsWith("'") && t.endsWith("'"))
  ) {
    try {
      const parsed = JSON.parse(t);
      if (typeof parsed === 'string') return parsed;
    } catch {
      return t.slice(1, -1).trim();
    }
  }
  return raw;
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
    blocks.push({ name, body: unwrapToolResponseBody(stepText.slice(start, end).trim()) });
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

function tryFormatWeatherSummary(name: string, body: string): string | null {
  const hint = `${name}\n${body}`;
  if (!/getWeather|天气查询|天气：|气温：|风力：/i.test(hint)) {
    return null;
  }
  if (/天气查询失败/.test(body)) {
    const msg = body.replace(/^天气查询失败[：:]\s*/i, '').trim();
    return msg ? `天气查询未成功：${msg}` : '天气查询未成功。';
  }
  if (/天气查询成功（实况）/.test(body)) {
    const city = matchKV(body, ['城市']);
    const weather = matchKV(body, ['天气']);
    const temp = matchKV(body, ['气温']);
    const humidity = matchKV(body, ['湿度']);
    const windDir = matchKV(body, ['风向']);
    const windPower = matchKV(body, ['风力']);
    const report = matchKV(body, ['发布时间']);
    const bits: string[] = [];
    if (city) bits.push(`**${city}**`);
    const cond: string[] = [];
    if (weather) cond.push(`**${weather}**`);
    if (temp) cond.push(`气温 **${temp}**`);
    if (cond.length) bits.push(`当前为 ${cond.join('，')}`);
    const wind: string[] = [];
    if (humidity) wind.push(`湿度 ${humidity}`);
    if (windDir || windPower) {
      wind.push([windDir, windPower].filter(Boolean).join(' '));
    }
    if (wind.length) bits.push(wind.join('，'));
    if (report) bits.push(`数据发布时间：${report}`);
    if (bits.length === 0) return null;
    return bits.join('；') + '。';
  }
  if (/天气查询成功（预报）/.test(body)) {
    const rest = body.replace(/^天气查询成功（预报）\n?/i, '').trim();
    const formatted = formatToolBody(rest);
    return formatted ? `**未来天气概况**\n\n${formatted}` : null;
  }
  return null;
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

function toolDisplayTitle(toolName: string): string {
  const key = toolName.trim();
  return TOOL_SECTION_TITLE[key] ?? '查询结果';
}

/**
 * 「思考与执行过程」折叠区：隐藏仅包含内部终止工具的步骤，避免暴露 doTerminate。
 */
export function filterManusStepsForCollapse(steps: string[]): string[] {
  return steps.filter((step) => {
    const tools = parseToolBlocks(step.trim());
    if (tools.length !== 1) return true;
    const n = tools[0].name.trim().toLowerCase();
    return !INTERNAL_TOOL_NAMES.has(n);
  });
}

/**
 * 将多步工具日志整理为 Markdown，供「综合回复」展示。
 */
export function summarizeManusSteps(steps: string[]): string {
  const useful = steps.filter((s) => !isNoiseStep(s));
  const toolSections: Array<{ title: string; text: string }> = [];
  const freeTextSections: string[] = [];

  for (const step of useful) {
    const tools = parseToolBlocks(step);
    if (tools.length > 0) {
      for (const { name, body } of tools) {
        const nameKey = name.trim().toLowerCase();
        if (INTERNAL_TOOL_NAMES.has(nameKey)) continue;
        if (!isMeaningfulBody(body)) continue;

        const weather = tryFormatWeatherSummary(name, body);
        const timeSummary = weather ? null : tryFormatTimeSummary(name, body);
        let text = (weather ?? timeSummary ?? formatToolBody(body)).trim();
        if (!text) continue;

        const title =
          weather != null
            ? '天气'
            : timeSummary != null
              ? '时间'
              : toolDisplayTitle(name);
        toolSections.push({ title, text });
      }
      continue;
    }
    const text = step.trim();
    if (text) freeTextSections.push(text);
  }

  if (toolSections.length === 0 && freeTextSections.length === 0) {
    return '';
  }

  const out: string[] = [];
  const multi = toolSections.length + freeTextSections.length > 1;
  if (multi) {
    out.push('已根据工具结果整理如下：', '');
  }

  for (const { title, text } of toolSections) {
    if (multi || toolSections.length > 1) {
      out.push(`#### ${title}`);
      out.push('');
    }
    out.push(text);
    out.push('');
  }

  for (const text of freeTextSections) {
    out.push(text);
    out.push('');
  }

  if (multi) {
    out.push('如需进一步细化行程或补充说明，可以继续提问。');
  }
  return out.join('\n').trim();
}
