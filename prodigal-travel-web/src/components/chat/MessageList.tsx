import {
  CaretRightOutlined,
  RobotOutlined,
  UserOutlined,
} from '@ant-design/icons';
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
import {
  filterManusStepsForCollapse,
  summarizeManusSteps,
} from '@/utils/manusStepSummary';

export interface MessageListProps {
  messages: ChatMessage[];
  bubbleVariant?: StreamChatKind;
  emptyHint?: string;
}

const { Text } = Typography;

const BUBBLE_MAX = 560;

function preserveSingleNewlinesForMarkdown(md: string): string {
  // Markdown 里「单个换行」默认会被当成空格；这里把它转成 hard break（两个空格 + \n）
  // 注意：这是展示层面的格式化，不改变后端原始内容。
  return md.replace(/\r\n/g, '\n').replace(/\n/g, '  \n');
}

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
    const emptyTextStyle: React.CSSProperties = {
      fontSize: 15,
      lineHeight: 1.8,
      color: 'rgba(226, 232, 240, 0.9)',
      textShadow: '0 1px 10px rgba(2, 6, 23, 0.55)',
    };
    return (
      <Empty
        description={
          <Text style={emptyTextStyle}>
            {emptyHint ??
              '你好！我是 AI 旅游助手。请用「对话列表」「示例问题」管理会话与发起咨询（窄屏在输入区上方，宽屏在两侧）。'}
          </Text>
        }
      />
    );
  }

  const assistantBaseStyle: React.CSSProperties = {
    borderColor: 'rgba(56, 189, 248, 0.28)',
    background: 'rgba(2, 6, 23, 0.16)',
    color: 'rgba(226, 232, 240, 0.92)',
  };

  const assistantBubbleStyle: React.CSSProperties =
    bubbleVariant === 'manus'
      ? {
          borderColor: 'rgba(167, 139, 250, 0.35)',
          background: 'rgba(114, 46, 209, 0.05)',
          color: 'rgba(226, 232, 240, 0.92)',
        }
      : assistantBaseStyle;

  return (
    <List
      size="small"
      split={false}
      dataSource={messages}
      renderItem={(m) => {
        const isUser = m.role === 'user';
        const manusStepsDisplay =
          bubbleVariant === 'manus' && m.manusSteps?.length
            ? filterManusStepsForCollapse(m.manusSteps)
            : [];
        const showManusPanel =
          bubbleVariant === 'manus' && manusStepsDisplay.length > 0;
        const manusSummaryFallback =
          bubbleVariant === 'manus' &&
          !isUser &&
          !m.streaming &&
          !m.content?.trim() &&
          m.manusSteps &&
          m.manusSteps.length > 0
            ? summarizeManusSteps(m.manusSteps)
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
                variant={isUser ? 'borderless' : 'outlined'}
                style={{
                  maxWidth: `min(88%, ${BUBBLE_MAX}px)`,
                  minWidth: 0,
                  flexShrink: 0,
                  overflow: 'hidden',
                  ...(!isUser ? assistantBubbleStyle : {}),
                }}
                styles={{
                  body: isUser
                    ? {
                        padding: '10px 14px',
                        background: token.colorPrimaryBg,
                      }
                    : {
                        padding: '10px 14px',
                        background: 'transparent',
                      },
                }}
              >
                {!isUser ? (
                  <Flex vertical gap="small">
                    {showManusPanel ? (
                      <Collapse
                        key={`${m.id}-think`}
                        size="small"
                        defaultActiveKey={m.streaming ? (['think'] as string[]) : []}
                        rootClassName="manus-think-collapse"
                        expandIcon={({ isActive }) => (
                          <CaretRightOutlined
                            rotate={isActive ? 90 : 0}
                            style={{
                              color: 'rgba(226, 232, 240, 0.92)',
                              fontSize: 12,
                            }}
                          />
                        )}
                        items={[
                          {
                            key: 'think',
                            label: `思考与执行过程（${manusStepsDisplay.length} 步）`,
                            children: (
                              <Flex vertical gap={8}>
                                {manusStepsDisplay.map((step, i) => (
                                  <Card key={i} size="small" type="inner">
                                    <Text type="secondary" style={{ fontSize: 12 }}>
                                      步骤 {i + 1}
                                    </Text>
                                    <Typography.Paragraph
                                      style={{
                                        marginBottom: 0,
                                        whiteSpace: 'pre-wrap',
                                        wordBreak: 'break-word',
                                        overflowWrap: 'anywhere',
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
                    {showManusPanel && manusReplyMd ? (
                      <Text
                        style={{
                          fontSize: 12,
                          color: 'rgba(191, 219, 254, 0.98)',
                          fontWeight: 600,
                          letterSpacing: 0.2,
                          textShadow: '0 0 10px rgba(30, 64, 175, 0.35)',
                        }}
                      >
                        综合回复
                      </Text>
                    ) : null}
                    {manusReplyMd ? (
                      <div className={bubbleVariant === 'manus' ? 'chat-markdown chat-markdown-manus' : 'chat-markdown'}>
                        <ReactMarkdown
                          remarkPlugins={[remarkGfm]}
                          rehypePlugins={[rehypeSanitize]}
                          components={markdownComponents}
                        >
                          {preserveSingleNewlinesForMarkdown(manusReplyMd)}
                        </ReactMarkdown>
                      </div>
                    ) : null}
                    {m.streaming && !m.content?.trim() && !manusSummaryFallback ? (
                      <Spin size="small" />
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
