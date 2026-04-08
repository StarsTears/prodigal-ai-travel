import { UserOutlined, RobotOutlined } from '@ant-design/icons';
import {
  Avatar,
  Card,
  Collapse,
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
import type { StreamChatKind } from '@/pages/chat/hooks/useStreaming';
import { isImageUrl } from '@/utils/imageUrl';
import { summarizeManusSteps } from '@/utils/manusStepSummary';

export interface MessageListProps {
  messages: ChatMessage[];
  bubbleVariant?: StreamChatKind;
  emptyHint?: string;
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

export const MessageList: React.FC<MessageListProps> = ({
  messages,
  bubbleVariant = 'travel',
  emptyHint,
}) => {
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
            {emptyHint ??
              '你好！我是 AI 旅游助手。请用「对话列表」「示例问题」管理会话与发起咨询（窄屏在输入区上方，宽屏在两侧）。'}
          </Text>
        }
      />
    );
  }

  const manusBubbleStyle: React.CSSProperties =
    bubbleVariant === 'manus'
      ? {
          borderColor: 'rgba(114, 46, 209, 0.35)',
          background: 'rgba(114, 46, 209, 0.04)',
        }
      : {};

  return (
    <List
      size="small"
      split={false}
      dataSource={messages}
      renderItem={(m) => {
        const isUser = m.role === 'user';
        const showManusPanel =
          bubbleVariant === 'manus' &&
          m.manusSteps &&
          m.manusSteps.length > 0;
        const manusSummaryFallback =
          bubbleVariant === 'manus' &&
          !isUser &&
          !m.streaming &&
          !m.content?.trim() &&
          showManusPanel
            ? summarizeManusSteps(m.manusSteps!)
            : '';
        const manusReplyMd =
          (m.content?.trim() && m.content) || manusSummaryFallback || '';
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
                  ...(!isUser ? manusBubbleStyle : {}),
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
                    {showManusPanel ? (
                      <Collapse
                        key={`${m.id}-think`}
                        size="small"
                        defaultActiveKey={m.streaming ? (['think'] as string[]) : []}
                        items={[
                          {
                            key: 'think',
                            label: `思考与执行过程（${m.manusSteps!.length} 步）`,
                            children: (
                              <Flex vertical gap={8}>
                                {m.manusSteps!.map((step, i) => (
                                  <Card key={i} size="small" type="inner">
                                    <Text type="secondary" style={{ fontSize: 12 }}>
                                      步骤 {i + 1}
                                    </Text>
                                    <Typography.Paragraph
                                      style={{
                                        marginBottom: 0,
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-word',
                                      }}
                                    >
                                      {step}
                                    </Typography.Paragraph>
                                  </Card>
                                ))}
                              </Flex>
                            ),
                          },
                        ]}
                      />
                    ) : null}
                    {showManusPanel ? (
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        综合回复
                      </Text>
                    ) : null}
                    {manusReplyMd ? (
                      <ReactMarkdown
                        remarkPlugins={[remarkGfm]}
                        rehypePlugins={[rehypeSanitize]}
                        components={markdownComponents}
                      >
                        {manusReplyMd}
                      </ReactMarkdown>
                    ) : null}
                    {m.streaming && !m.content?.trim() && !manusSummaryFallback ? (
                      <Spin size="small" />
                    ) : null}
                    {!m.streaming &&
                    !manusReplyMd &&
                    showManusPanel ? (
                      <Text type="secondary" style={{ fontSize: 13 }}>
                        暂无综合回复；请展开上方「思考与执行过程」查看详情。
                      </Text>
                    ) : null}
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
