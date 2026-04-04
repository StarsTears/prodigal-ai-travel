import { UserOutlined, RobotOutlined } from '@ant-design/icons';
import {
  Avatar,
  Card,
  Empty,
  Flex,
  List,
  Spin,
  theme,
  Typography,
} from 'antd';
import React, { useEffect, useRef } from 'react';
import ReactMarkdown from 'react-markdown';
import type { Components } from 'react-markdown';
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import type { ChatMessage } from '@/types';
import { isImageUrl } from '@/utils/imageUrl';

export interface MessageListProps {
  messages: ChatMessage[];
}

const { Text } = Typography;

const BUBBLE_MAX = 560;

function markdownImageStyle(borderRadius: number): React.CSSProperties {
  return {
    maxWidth: '100%',
    height: 'auto',
    display: 'block',
    borderRadius,
    verticalAlign: 'middle',
  };
}

export const MessageList: React.FC<MessageListProps> = ({ messages }) => {
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const { token } = theme.useToken();

  const markdownComponents: Components = {
    a: ({ href, children, node: _node, ...rest }) => {
      if (href && isImageUrl(href)) {
        const alt =
          typeof children === 'string'
            ? children
            : '图片';
        return (
          <a
            {...rest}
            href={href}
            target="_blank"
            rel="noopener noreferrer"
            style={{ display: 'block', marginTop: 8, marginBottom: 8 }}
          >
            <img
              src={href}
              alt={alt}
              loading="lazy"
              decoding="async"
              style={markdownImageStyle(token.borderRadius)}
            />
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
      <img
        {...rest}
        src={src}
        alt={alt ?? ''}
        loading="lazy"
        decoding="async"
        style={markdownImageStyle(token.borderRadius)}
      />
    ),
  };

  useEffect(() => {
    bottomRef.current?.scrollIntoView({ behavior: 'smooth', block: 'end' });
  }, [messages]);

  if (messages.length === 0) {
    return (
      <Empty
        description={
          <Text type="secondary">
            你好！我是 AI 旅游助手。请用「对话列表」「示例问题」管理会话与发起咨询（窄屏在输入区上方，宽屏在两侧）。
          </Text>
        }
      />
    );
  }

  return (
    <List
      size="small"
      split={false}
      dataSource={messages}
      renderItem={(m) => {
        const isUser = m.role === 'user';
        return (
          <List.Item
            key={m.id}
            style={{
              display: 'block',
              width: '100%',
              padding: '10px 20px',
              border: 'none',
            }}
          >
            <Flex
              style={{ width: '100%' }}
              justify={isUser ? 'flex-end' : 'flex-start'}
              align="flex-start"
              gap="middle"
              wrap="nowrap"
            >
              {!isUser ? <Avatar icon={<RobotOutlined />} /> : null}
              <Card
                size="small"
                bordered={!isUser}
                style={{
                  maxWidth: `min(88%, ${BUBBLE_MAX}px)`,
                  flexShrink: 0,
                }}
                styles={{
                  body: isUser
                    ? {
                        padding: '10px 14px',
                        background: token.colorPrimaryBg,
                      }
                    : { padding: '10px 14px' },
                }}
              >
                {!isUser ? (
                  <Flex vertical gap="small">
                    <ReactMarkdown
                      remarkPlugins={[remarkGfm]}
                      rehypePlugins={[rehypeSanitize]}
                      components={markdownComponents}
                    >
                      {m.content || (m.streaming ? ' ' : '')}
                    </ReactMarkdown>
                    {m.streaming && !m.content ? <Spin size="small" /> : null}
                  </Flex>
                ) : (
                  <Text style={{ whiteSpace: 'pre-wrap', wordBreak: 'break-word' }}>
                    {m.content}
                  </Text>
                )}
              </Card>
              {isUser ? <Avatar icon={<UserOutlined />} /> : null}
            </Flex>
          </List.Item>
        );
      }}
      footer={<div ref={bottomRef} />}
    />
  );
};
