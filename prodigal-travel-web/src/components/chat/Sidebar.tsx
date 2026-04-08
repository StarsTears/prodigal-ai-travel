import { DeleteOutlined, PlusOutlined } from '@ant-design/icons';
import { Button, Empty, Flex, Popconfirm, Spin, Typography, theme } from 'antd';
import React, { useMemo } from 'react';
import type { ConversationRecord } from '@/types';
import { UserSessionBlock } from '@/components/chat/UserSessionBlock';

type DateBucketKey = 'today' | 'yesterday' | 'week' | 'month' | 'older';

const DATE_BUCKET_ORDER: DateBucketKey[] = [
  'today',
  'yesterday',
  'week',
  'month',
  'older',
];

const DATE_BUCKET_LABEL: Record<DateBucketKey, string> = {
  today: '今天',
  yesterday: '昨天',
  week: '7 天内',
  month: '30 天内',
  older: '更早',
};

const MS_PER_DAY = 86_400_000;

/** 按本地日历日将 `updatedAt` 归入 DeepSeek 式分组（今天 / 昨天 / 7 天内 / 30 天内 / 更早）。 */
function bucketForUpdatedAt(updatedAt: number): DateBucketKey {
  const ts = Math.min(updatedAt, Date.now());
  const now = new Date();
  const startToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate()
  ).getTime();
  const startYesterday = startToday - MS_PER_DAY;
  const startWeek = startToday - 7 * MS_PER_DAY;
  const startMonth = startToday - 30 * MS_PER_DAY;
  if (ts >= startToday) return 'today';
  if (ts >= startYesterday) return 'yesterday';
  if (ts >= startWeek) return 'week';
  if (ts >= startMonth) return 'month';
  return 'older';
}

function groupConversationsByDate(
  conversations: ConversationRecord[]
): { key: DateBucketKey; label: string; items: ConversationRecord[] }[] {
  const buckets: Record<DateBucketKey, ConversationRecord[]> = {
    today: [],
    yesterday: [],
    week: [],
    month: [],
    older: [],
  };
  for (const c of conversations) {
    buckets[bucketForUpdatedAt(c.updatedAt)].push(c);
  }
  return DATE_BUCKET_ORDER.map((key) => ({
    key,
    label: DATE_BUCKET_LABEL[key],
    items: buckets[key],
  })).filter((g) => g.items.length > 0);
}

export interface SidebarProps {
  conversations: ConversationRecord[];
  activeId: string | null;
  onNew: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void | Promise<void>;
  /** 未登录时点击打开登录弹窗 */
  onOpenLogin?: () => void;
  /**
   * 已移除侧栏搜索框；保留可选字段并默认 `''`，避免旧代码或合并残留仍引用 `search` 时报 ReferenceError。
   */
  search?: string;
  /** 已登录且正在从服务端拉取会话列表 */
  listLoading?: boolean;
}

const { Text } = Typography;

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  activeId,
  onNew,
  onSelect,
  onDelete,
  onOpenLogin,
  search = '',
  listLoading = false,
}) => {
  const { token: themeToken } = theme.useToken();
  const textPrimary = 'rgba(226, 232, 240, 0.94)';
  const textSecondary = 'rgba(148, 163, 184, 0.9)';

  const groupedConversations = useMemo(
    () => groupConversationsByDate(conversations),
    [conversations]
  );

  return (
    <Flex
      vertical
      style={{
        height: '100%',
        minHeight: 0,
        padding: '22px 12px 12px',
        boxSizing: 'border-box',
        gap: 0,
      }}
      data-deprecated-sidebar-search={search || undefined}
    >
      <Flex
        align="center"
        gap={10}
        style={{ flexShrink: 0, marginBottom: 22 }}
      >
        <img
          src="/logo.svg"
          alt=""
          width={28}
          height={28}
          style={{ display: 'block', flexShrink: 0 }}
        />
        <Typography.Text strong style={{ fontSize: 15, color: textPrimary }}>
          AI 旅游助手
        </Typography.Text>
      </Flex>
      <div style={{ flexShrink: 0, marginBottom: 18 }}>
        <Button type="primary" icon={<PlusOutlined />} block onClick={onNew}>
          新对话
        </Button>
      </div>
      <div
        className="prodigal-sidebar-conv-scroll"
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
          position: 'relative',
          paddingRight: 2,
        }}
      >
        {listLoading ? (
          <Flex align="center" justify="center" style={{ minHeight: 120 }}>
            <Spin />
          </Flex>
        ) : conversations.length === 0 ? (
          <Empty
            description={
              <Text style={{ color: textSecondary }}>
                暂无对话
              </Text>
            }
          />
        ) : (
          <Flex vertical gap={0}>
            {groupedConversations.map((group, gi) => (
              <div key={group.key}>
                <Text
                  type="secondary"
                  style={{
                    display: 'block',
                    fontSize: 12,
                    lineHeight: '16px',
                    marginTop: gi === 0 ? 0 : 18,
                    marginBottom: 10,
                    paddingLeft: 4,
                    color: textSecondary,
                  }}
                >
                  {group.label}
                </Text>
                <Flex vertical gap={6}>
                  {group.items.map((c) => {
                    const selected = activeId === c.id;
                    return (
                      <Flex
                        key={c.id}
                        align="center"
                        gap={8}
                        role="button"
                        tabIndex={0}
                        onClick={() => onSelect(c.id)}
                        onKeyDown={(e) => {
                          if (e.key === 'Enter' || e.key === ' ') {
                            e.preventDefault();
                            onSelect(c.id);
                          }
                        }}
                        style={{
                          padding: '9px 10px',
                          borderRadius: themeToken.borderRadiusLG,
                          cursor: 'pointer',
                          background: selected
                            ? 'rgba(59, 130, 246, 0.22)'
                            : 'transparent',
                          border: selected
                            ? '1px solid rgba(125, 211, 252, 0.4)'
                            : '1px solid transparent',
                          transition: 'background 0.15s ease',
                          outline: 'none',
                        }}
                      >
                        <Typography.Text
                          ellipsis={{ tooltip: c.title }}
                          style={{
                            flex: 1,
                            minWidth: 0,
                            fontSize: 14,
                            color: selected
                              ? '#bfdbfe'
                              : textPrimary,
                          }}
                        >
                          {c.title}
                        </Typography.Text>
                        <Popconfirm
                          title="删除此对话？"
                          okText="删除"
                          cancelText="取消"
                          onConfirm={(e) => {
                            e?.stopPropagation?.();
                            void onDelete(c.id);
                          }}
                        >
                          <Button
                            type="text"
                            size="small"
                            danger
                            icon={<DeleteOutlined />}
                            onClick={(e) => e.stopPropagation()}
                            aria-label="删除对话"
                          />
                        </Popconfirm>
                      </Flex>
                    );
                  })}
                </Flex>
              </div>
            ))}
          </Flex>
        )}
      </div>
      <div
        style={{
          flexShrink: 0,
          marginTop: 'auto',
          paddingTop: 12,
          borderTop: '1px solid rgba(255, 255, 255, 0.1)',
        }}
      >
        <UserSessionBlock onOpenLogin={onOpenLogin} />
      </div>
    </Flex>
  );
};
