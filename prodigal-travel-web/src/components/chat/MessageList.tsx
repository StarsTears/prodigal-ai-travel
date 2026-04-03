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
import rehypeSanitize from 'rehype-sanitize';
import remarkGfm from 'remark-gfm';
import type { ChatMessage } from '@/types';

export interface MessageListProps {
  messages: ChatMessage[];
}

const { Text } = Typography;

const BUBBLE_MAX = 560;

export const MessageList: React.FC<MessageListProps> = ({ messages }) => {
  const bottomRef = useRef<HTMLDivElement | null>(null);
  const { token } = theme.useToken();

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
