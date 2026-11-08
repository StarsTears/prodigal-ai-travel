import {
  ArrowDownOutlined,
  CheckOutlined,
  CopyOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { message } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import { quickPromptsFor } from '@/components/chat/ToolPanel';
import type { StreamChatKind } from '@/pages/chat/hooks/useStreaming';
import type { ChatMessage } from '@/types';
import {
  ASSISTANT_AVATAR_ALT,
  ASSISTANT_AVATAR_URL,
} from '@/utils/preferences';
import {
  filterManusStepsForCollapse,
  summarizeManusSteps,
} from '@/utils/manusStepSummary';

export interface MessageListProps {
  messages: ChatMessage[];
  bubbleVariant?: StreamChatKind;
  emptyHint?: string;
  /** 空态欢迎卡中的示例问题回调 */
  onQuickPrompt?: (text: string) => void;
}

/** Markdown 依赖较重，延迟到出现第一条助手回复时再加载。 */
const MarkdownContent = React.lazy(
  () => import('@/components/chat/MarkdownContent'),
);

function formatTime(ts: number): string {
  if (!Number.isFinite(ts) || ts <= 0) return '';
  try {
    return new Date(ts).toLocaleTimeString('zh-CN', {
      hour: '2-digit',
      minute: '2-digit',
    });
  } catch {
    return '';
  }
}

export const MessageList: React.FC<MessageListProps> = ({
  messages,
  bubbleVariant = 'travel',
  emptyHint,
  onQuickPrompt,
}) => {
  const scrollRef = useRef<HTMLDivElement>(null);
  const atBottomRef = useRef(true);
  const prevLengthRef = useRef(messages.length);
  const [showJump, setShowJump] = useState(false);
  const [copiedId, setCopiedId] = useState<string | null>(null);

  /** 仅当用户停留在底部附近时才跟随流式输出；回看历史不会被强行拉回。 */
  useEffect(() => {
    const el = scrollRef.current;
    if (!el) return;
    const grew = messages.length > prevLengthRef.current;
    const lastIsUser = messages[messages.length - 1]?.role === 'user';
    prevLengthRef.current = messages.length;
    if (grew && lastIsUser) {
      atBottomRef.current = true;
      setShowJump(false);
    }
    if (atBottomRef.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);

  const onScroll = () => {
    const el = scrollRef.current;
    if (!el) return;
    const nearBottom = el.scrollHeight - el.scrollTop - el.clientHeight < 96;
    atBottomRef.current = nearBottom;
    setShowJump(!nearBottom);
  };

  const jumpToLatest = () => {
    const el = scrollRef.current;
    if (!el) return;
    atBottomRef.current = true;
    setShowJump(false);
    el.scrollTo({ top: el.scrollHeight, behavior: 'smooth' });
  };

  const copyContent = async (m: ChatMessage) => {
    const text = m.content || '';
    if (!text) return;
    try {
      await navigator.clipboard.writeText(text);
      setCopiedId(m.id);
      window.setTimeout(() => setCopiedId(null), 1600);
    } catch {
      message.warning('复制失败，请手动选中文本');
    }
  };

  if (messages.length === 0) {
    const isManus = bubbleVariant === 'manus';
    return (
      <div className="chat-scroll chat-scroll--empty">
        <div className="chat-empty">
          <div className={`chat-empty__icon ${isManus ? 'chat-empty__icon--manus' : ''}`}>
            <img src={ASSISTANT_AVATAR_URL} alt={ASSISTANT_AVATAR_ALT} />
          </div>
          <h2 className="chat-empty__title">
            {isManus ? '交给智能体，把任务拆开做完' : '今天想去哪儿？'}
          </h2>
          <p className="chat-empty__desc">
            {emptyHint ??
              (isManus
                ? '描述一个较复杂的目标，智能体会逐步推理、调用工具，并把过程与结论分开呈现。'
                : '问路线、查天气、找景点，或切换到「知识库问答」只依据已入库资料作答。')}
          </p>
          <div className="quick-prompt-grid quick-prompt-grid--empty">
            {quickPromptsFor(bubbleVariant).map((item) => (
              <button
                key={item.prompt}
                type="button"
                className="quick-prompt"
                onClick={() => onQuickPrompt?.(item.prompt)}
              >
                <span className="quick-prompt__label">
                  {item.icon}　{item.label}
                </span>
                <span className="quick-prompt__text">{item.prompt}</span>
              </button>
            ))}
          </div>
        </div>
      </div>
    );
  }

  return (
    <>
      <div className="chat-scroll" ref={scrollRef} onScroll={onScroll}>
        <div className="chat-thread">
          {messages.map((m) => {
            const isUser = m.role === 'user';
            const manusSteps =
              bubbleVariant === 'manus' && m.manusSteps?.length
                ? filterManusStepsForCollapse(m.manusSteps)
                : [];
            const showSteps = !isUser && manusSteps.length > 0;
            const summaryFallback =
              bubbleVariant === 'manus' &&
              !isUser &&
              !m.streaming &&
              !m.content?.trim() &&
              m.manusSteps?.length
                ? summarizeManusSteps(m.manusSteps)
                : '';
            const reply = (m.content?.trim() && m.content) || summaryFallback || '';
            const time = formatTime(m.createdAt);
            const waiting = Boolean(m.streaming) && !isUser && !reply && !showSteps;

            return (
              <div
                key={m.id}
                className={`chat-row ${isUser ? 'chat-row--user' : 'chat-row--assistant'}`}
              >
                <div
                  className={`chat-avatar ${
                    isUser
                      ? 'chat-avatar--user'
                      : bubbleVariant === 'manus'
                        ? 'chat-avatar--manus'
                        : 'chat-avatar--assistant'
                  }`}
                >
                  {isUser ? (
                    <UserOutlined />
                  ) : (
                    <img
                      className="chat-avatar__img"
                      src={ASSISTANT_AVATAR_URL}
                      alt={ASSISTANT_AVATAR_ALT}
                    />
                  )}
                </div>
                <div className="chat-bubble-wrap">
                  <div className="chat-meta">
                    <span>{isUser ? '我' : bubbleVariant === 'manus' ? '超级智能体' : 'AI 旅游助手'}</span>
                    {time ? <span>· {time}</span> : null}
                    {m.streaming && !isUser ? (
                      <span className="streaming-label">
                        <span className="streaming-dot" />
                        正在生成
                      </span>
                    ) : null}
                  </div>

                  {isUser ? (
                    <div className="chat-bubble chat-bubble--user">{m.content}</div>
                  ) : (
                    <div
                      className={`chat-bubble chat-bubble--assistant ${
                        bubbleVariant === 'manus' ? 'chat-bubble--manus' : ''
                      }`}
                    >
                      {showSteps ? (
                        <details className="manus-steps" open={m.streaming ? true : undefined}>
                          <summary>
                            <span>思考与执行过程</span>
                            <span className="ui-chip">{manusSteps.length} 步</span>
                          </summary>
                          <div className="manus-steps__body">
                            {manusSteps.map((step, i) => (
                              <div className="manus-step" key={`${m.id}-step-${i}`}>
                                <span className="manus-step__index">步骤 {i + 1}</span>
                                {step}
                              </div>
                            ))}
                          </div>
                        </details>
                      ) : null}

                      {showSteps && reply ? (
                        <div className="chat-reply-label" style={{ marginTop: 12 }}>
                          综合回复
                        </div>
                      ) : null}

                      {reply ? (
                        <div className="chat-markdown" style={{ marginTop: showSteps ? 6 : 0 }}>
                          <React.Suspense
                            fallback={
                              <div className="chat-markdown__fallback">{reply}</div>
                            }
                          >
                            <MarkdownContent text={reply} />
                          </React.Suspense>
                        </div>
                      ) : null}

                      {waiting ? (
                        <div className="streaming-label">
                          <span className="streaming-dot" />
                          正在整理思路…
                        </div>
                      ) : null}
                    </div>
                  )}

                  {!isUser && reply ? (
                    <div className="chat-actions">
                      <button
                        type="button"
                        className="chat-action"
                        onClick={() => void copyContent(m)}
                      >
                        {copiedId === m.id ? <CheckOutlined /> : <CopyOutlined />}
                        {copiedId === m.id ? '已复制' : '复制'}
                      </button>
                    </div>
                  ) : null}
                </div>
              </div>
            );
          })}
        </div>
      </div>
      {showJump ? (
        <button type="button" className="chat-jump-latest" onClick={jumpToLatest}>
          <ArrowDownOutlined /> 回到最新
        </button>
      ) : null}
    </>
  );
};
