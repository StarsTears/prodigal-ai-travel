import React from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import { isImageUrl } from '@/utils/imageUrl';

/**
 * Markdown 渲染器独立成文件，由 MessageList 通过 React.lazy 按需加载。
 * react-markdown + remark-gfm + rehype-sanitize 约 200KB（gzip ~64KB），
 * 空态欢迎页 / 纯用户消息并不需要它，延迟到第一条助手回复再下载。
 */
export interface MarkdownContentProps {
  text: string;
}

/**
 * Markdown 的「单个换行」默认会被当作空格，这里转成 hard break。
 * 跳过列表 / 表格 / 引用 / 标题 / 代码块行，避免破坏 GFM 结构。
 */
function preserveSingleNewlinesForMarkdown(md: string): string {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  let inFence = false;
  return lines
    .map((line, i) => {
      const trimmed = line.trimEnd();
      if (/^\s*(```|~~~)/.test(trimmed)) {
        inFence = !inFence;
        return line;
      }
      if (inFence || i === lines.length - 1 || !trimmed || trimmed.endsWith('  ')) {
        return line;
      }
      // 结构性行保持原样，否则会被追加两个空格导致列表/表格解析异常
      if (/^\s{0,3}([-*+]\s|\d+\.\s|>|#{1,6}\s|\||-{3,})/.test(trimmed)) {
        return line;
      }
      return `${line}  `;
    })
    .join('\n');
}

const markdownComponents: Components = {
  a: ({ href, children, node: _node, ...rest }) => {
    if (href && isImageUrl(href)) {
      return (
        <a
          {...rest}
          href={href}
          target="_blank"
          rel="noopener noreferrer"
          style={{ display: 'block', margin: '8px 0' }}
        >
          <img src={href} alt="图片" loading="lazy" decoding="async" />
        </a>
      );
    }
    return (
      <a {...rest} href={href} target="_blank" rel="noopener noreferrer">
        {children}
      </a>
    );
  },
  img: ({ src, alt, node: _node, ...rest }) => (
    <img {...rest} src={src} alt={alt ?? ''} loading="lazy" decoding="async" />
  ),
};

const MarkdownContent: React.FC<MarkdownContentProps> = ({ text }) => (
  <ReactMarkdown
    remarkPlugins={[remarkGfm]}
    rehypePlugins={[rehypeSanitize]}
    components={markdownComponents}
  >
    {preserveSingleNewlinesForMarkdown(text)}
  </ReactMarkdown>
);

export default MarkdownContent;
