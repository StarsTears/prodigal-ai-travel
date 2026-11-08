import { DeleteOutlined, PlusOutlined, SearchOutlined } from '@ant-design/icons';
import { Button, Input, Popconfirm, Spin } from 'antd';
import React, { useMemo, useState } from 'react';
import { UserSessionBlock } from '@/components/chat/UserSessionBlock';
import type { ConversationRecord } from '@/types';

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

function bucketForUpdatedAt(updatedAt: number): DateBucketKey {
  const ts = Math.min(updatedAt, Date.now());
  const now = new Date();
  const startToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate()
  ).getTime();
  if (ts >= startToday) return 'today';
  if (ts >= startToday - MS_PER_DAY) return 'yesterday';
  if (ts >= startToday - 7 * MS_PER_DAY) return 'week';
  if (ts >= startToday - 30 * MS_PER_DAY) return 'month';
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

/** 会话标题来自首句提问，展示前做一次清洗，避免换行/空白把列表撑乱。 */
function cleanTitle(title: string): string {
  const t = (title ?? '').replace(/\s+/g, ' ').trim();
  return t || '新对话';
}

export interface SidebarProps {
  conversations: ConversationRecord[];
  activeId: string | null;
  onNew: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void | Promise<void>;
  onOpenLogin?: () => void;
  /**
   * 是否在底部显示用户/登录入口。
   * 宽屏时顶栏已有用户胶囊，这里不再重复；窄屏侧栏是抽屉，需要保留入口。
   */
  showSessionBlock?: boolean;
  listLoading?: boolean;
}

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  activeId,
  onNew,
  onSelect,
  onDelete,
  onOpenLogin,
  showSessionBlock = false,
  listLoading = false,
}) => {
  const [keyword, setKeyword] = useState('');

  const grouped = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    const filtered = q
      ? conversations.filter((c) => cleanTitle(c.title).toLowerCase().includes(q))
      : conversations;
    return groupConversationsByDate(filtered);
  }, [conversations, keyword]);

  return (
    <div className="chat-sider-inner">
      <div className="sidebar">
        {/* 品牌与账号信息统一由顶栏承载，侧栏只保留会话操作，避免重复 */}
        <button type="button" className="sidebar__new" onClick={onNew}>
          <PlusOutlined /> 新对话
        </button>

        <div className="sidebar__search">
          <Input
            size="small"
            allowClear
            prefix={<SearchOutlined />}
            placeholder="搜索会话"
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
          />
        </div>

        <div className="sidebar__list prodigal-sidebar-conv-scroll">
          {listLoading ? (
            <div style={{ display: 'flex', justifyContent: 'center', padding: 32 }}>
              <Spin />
            </div>
          ) : grouped.length === 0 ? (
            <div className="sidebar__empty">
              {keyword.trim()
                ? '没有匹配的对话，换个关键词试试'
                : '还没有对话，点上方「新对话」开始吧'}
            </div>
          ) : (
            grouped.map((group) => (
              <div key={group.key}>
                <span className="sidebar__group-label">{group.label}</span>
                {group.items.map((c) => {
                  const selected = activeId === c.id;
                  return (
                    <div
                      key={c.id}
                      role="button"
                      tabIndex={0}
                      className={`sidebar__item ${selected ? 'sidebar__item--active' : ''}`}
                      onClick={() => onSelect(c.id)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter' || e.key === ' ') {
                          e.preventDefault();
                          onSelect(c.id);
                        }
                      }}
                    >
                      <span className="sidebar__item-title" title={cleanTitle(c.title)}>
                        {cleanTitle(c.title)}
                      </span>
                      <Popconfirm
                        title="删除此对话？"
                        description="删除后不可恢复"
                        okText="删除"
                        cancelText="取消"
                        onConfirm={(e) => {
                          e?.stopPropagation?.();
                          void onDelete(c.id);
                        }}
                      >
                        <Button
                          className="sidebar__delete"
                          type="text"
                          size="small"
                          danger
                          icon={<DeleteOutlined />}
                          aria-label="删除对话"
                          onClick={(e) => e.stopPropagation()}
                        />
                      </Popconfirm>
                    </div>
                  );
                })}
              </div>
            ))
          )}
        </div>

        {showSessionBlock ? (
          <div className="sidebar__footer">
            <UserSessionBlock onOpenLogin={onOpenLogin} />
          </div>
        ) : null}
      </div>
    </div>
  );
};
