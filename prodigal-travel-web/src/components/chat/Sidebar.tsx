import {
  CheckOutlined,
  CloseOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons';
import { Button, Input, Popconfirm, Spin } from 'antd';
import React, { useMemo, useState } from 'react';
import { UserSessionBlock } from '@/components/chat/UserSessionBlock';
import type { ConversationRecord } from '@/types';

type DateBucketKey = 'today' | 'yesterday' | 'week' | 'older';

const DATE_BUCKET_ORDER: DateBucketKey[] = ['today', 'yesterday', 'week', 'older'];

const DATE_BUCKET_LABEL: Record<DateBucketKey, string> = {
  today: '今天',
  yesterday: '昨天',
  week: '7 天内',
  older: '更早',
};

const MS_PER_DAY = 86_400_000;

/** 按更新时间归入「今天 / 昨天 / 7天内 / 更早」四档，用于侧栏分组标题。 */
function bucketForUpdatedAt(updatedAt: number): DateBucketKey {
  const ts = Math.min(updatedAt, Date.now());
  const now = new Date();
  const startToday = new Date(
    now.getFullYear(),
    now.getMonth(),
    now.getDate(),
  ).getTime();
  if (ts >= startToday) return 'today';
  if (ts >= startToday - MS_PER_DAY) return 'yesterday';
  if (ts >= startToday - 7 * MS_PER_DAY) return 'week';
  return 'older';
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
  onRename?: (id: string, title: string) => void | Promise<void>;
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
  onRename,
  onOpenLogin,
  showSessionBlock = false,
  listLoading = false,
}) => {
  const [keyword, setKeyword] = useState('');
  const [editingId, setEditingId] = useState<string | null>(null);
  const [editValue, setEditValue] = useState('');

  const startRename = (c: ConversationRecord) => {
    setEditingId(c.id);
    setEditValue(cleanTitle(c.title));
  };

  const commitRename = () => {
    const id = editingId;
    const next = editValue.trim();
    setEditingId(null);
    setEditValue('');
    if (!id || !next) return;
    void onRename?.(id, next);
  };

  const cancelRename = () => {
    setEditingId(null);
    setEditValue('');
  };

  /** 侧边栏按时间分组建标题列表：今天 / 昨天 / 7天内 / 更早，每项只显示标题。 */
  const grouped = useMemo(() => {
    const q = keyword.trim().toLowerCase();
    const filtered = q
      ? conversations.filter((c) =>
          cleanTitle(c.title).toLowerCase().includes(q),
        )
      : conversations;
    const buckets: Record<DateBucketKey, ConversationRecord[]> = {
      today: [],
      yesterday: [],
      week: [],
      older: [],
    };
    for (const c of filtered) {
      buckets[bucketForUpdatedAt(c.updatedAt)].push(c);
    }
    return DATE_BUCKET_ORDER.map((key) => ({
      key,
      label: DATE_BUCKET_LABEL[key],
      items: buckets[key],
    })).filter((g) => g.items.length > 0);
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
              const editing = editingId === c.id;
              return (
                <div
                  key={c.id}
                  role="button"
                  tabIndex={0}
                  className={`sidebar__item ${selected ? 'sidebar__item--active' : ''} ${
                    editing ? 'sidebar__item--editing' : ''
                  }`}
                  onClick={() => !editing && onSelect(c.id)}
                  onKeyDown={(e) => {
                    if (editing) return;
                    if (e.key === 'Enter' || e.key === ' ') {
                      e.preventDefault();
                      onSelect(c.id);
                    }
                  }}
                >
                  {editing ? (
                    <div
                      className="sidebar__edit"
                      onClick={(e) => e.stopPropagation()}
                    >
                      <Input
                        size="small"
                        autoFocus
                        value={editValue}
                        maxLength={50}
                        onChange={(e) => setEditValue(e.target.value)}
                        onPressEnter={commitRename}
                        onKeyDown={(e) => {
                          if (e.key === 'Escape') cancelRename();
                        }}
                      />
                      <Button
                        className="sidebar__edit-btn"
                        type="text"
                        size="small"
                        icon={<CheckOutlined />}
                        aria-label="保存名称"
                        onClick={commitRename}
                      />
                      <Button
                        className="sidebar__edit-btn"
                        type="text"
                        size="small"
                        icon={<CloseOutlined />}
                        aria-label="取消重命名"
                        onClick={cancelRename}
                      />
                    </div>
                  ) : (
                    <>
                      <span
                        className="sidebar__item-title"
                        title={cleanTitle(c.title)}
                      >
                        {cleanTitle(c.title)}
                      </span>
                      <span className="sidebar__item-actions">
                        <Button
                          className="sidebar__rename"
                          type="text"
                          size="small"
                          icon={<EditOutlined />}
                          aria-label="重命名对话"
                          onClick={(e) => {
                            e.stopPropagation();
                            startRename(c);
                          }}
                        />
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
                      </span>
                    </>
                  )}
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
