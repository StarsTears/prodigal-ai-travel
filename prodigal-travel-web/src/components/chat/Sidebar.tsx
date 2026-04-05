import {
  DeleteOutlined,
  LoginOutlined,
  LogoutOutlined,
  MoreOutlined,
  PlusOutlined,
  SettingOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Button,
  Dropdown,
  Empty,
  Flex,
  Input,
  Modal,
  Popconfirm,
  Spin,
  Typography,
  message,
  theme,
} from 'antd';
import type { MenuProps } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import type { ConversationRecord } from '@/types';

const DISPLAY_NAME_KEY = 'prodigal-travel-display-name';

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
  const { user, logout, deregisterAccount, token } = useAuth();
  const isLoggedIn = Boolean(token);
  const accountLabel = useMemo(
    () =>
      user?.nickname ||
      user?.username ||
      user?.email?.split('@')[0] ||
      '用户',
    [user?.email, user?.nickname, user?.username]
  );
  const [localAlias, setLocalAlias] = useState('');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [nameDraft, setNameDraft] = useState('');

  useEffect(() => {
    try {
      const v = localStorage.getItem(DISPLAY_NAME_KEY);
      setLocalAlias(v?.trim() ? v.trim() : '');
    } catch {
      /* ignore */
    }
  }, []);

  const displayName =
    localAlias.trim() || (isLoggedIn ? accountLabel : '未登录');

  const openSettings = () => {
    setNameDraft(localAlias);
    setSettingsOpen(true);
  };

  const saveSettings = () => {
    const next = nameDraft.trim();
    setLocalAlias(next);
    try {
      if (next) {
        localStorage.setItem(DISPLAY_NAME_KEY, next);
      } else {
        localStorage.removeItem(DISPLAY_NAME_KEY);
      }
    } catch {
      /* ignore */
    }
    setSettingsOpen(false);
  };

  const confirmDeregister = () => {
    Modal.confirm({
      title: '确认注销账号？',
      content:
        '账号将被永久注销（逻辑删除），所有登录状态失效，数据不可恢复。',
      okText: '确认注销',
      okType: 'danger',
      cancelText: '取消',
      centered: true,
      onOk: async () => {
        try {
          await deregisterAccount();
        } catch (e) {
          message.error(e instanceof Error ? e.message : '注销失败');
          return Promise.reject(e);
        }
      },
    });
  };

  const profileMenuItems: MenuProps['items'] = [
    {
      key: 'settings',
      icon: <SettingOutlined />,
      label: '系统设置',
    },
    { type: 'divider' },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: '退出登录',
    },
    { type: 'divider' },
    {
      key: 'deregister',
      icon: <UserDeleteOutlined />,
      label: '注销账号',
      danger: true,
    },
  ];

  const onProfileMenuClick: MenuProps['onClick'] = ({ key, domEvent }) => {
    domEvent.stopPropagation();
    if (key === 'settings') {
      openSettings();
      return;
    }
    if (key === 'logout') {
      void logout();
      return;
    }
    if (key === 'deregister') {
      confirmDeregister();
    }
  };

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
        <Typography.Text strong style={{ fontSize: 15 }}>
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
          <Empty description={<Text type="secondary">暂无对话</Text>} />
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
                    color: themeToken.colorTextTertiary,
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
                            ? themeToken.colorPrimaryBg
                            : 'transparent',
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
                              ? themeToken.colorPrimary
                              : themeToken.colorText,
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
          borderTop: '1px solid rgba(0, 0, 0, 0.06)',
        }}
      >
        {isLoggedIn ? (
          <Dropdown
            menu={{ items: profileMenuItems, onClick: onProfileMenuClick }}
            trigger={['click']}
            placement="topRight"
            overlayClassName="prodigal-profile-dropdown"
            getPopupContainer={() => document.body}
          >
            <Flex
              align="center"
              gap={10}
              style={{
                width: '100%',
                padding: '8px 10px',
                borderRadius: themeToken.borderRadiusLG,
                cursor: 'pointer',
                transition: 'background 0.15s ease',
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background =
                  themeToken.colorFillTertiary;
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = 'transparent';
              }}
              aria-label="用户菜单"
            >
              <Avatar size={28} style={{ flexShrink: 0 }}>
                {displayName.slice(0, 1).toUpperCase()}
              </Avatar>
              <Typography.Text
                strong
                ellipsis
                style={{ flex: 1, minWidth: 0, fontSize: 14 }}
              >
                {displayName}
              </Typography.Text>
              <MoreOutlined
                style={{
                  color: themeToken.colorTextTertiary,
                  fontSize: 16,
                  flexShrink: 0,
                }}
              />
            </Flex>
          </Dropdown>
        ) : (
          <Flex align="center" gap="small" style={{ padding: '4px 0' }}>
            <Avatar size="small" style={{ flexShrink: 0 }}>
              访
            </Avatar>
            <Typography.Text ellipsis style={{ flex: 1, minWidth: 0 }}>
              {displayName}
            </Typography.Text>
            <Button
              type="link"
              size="small"
              icon={<LoginOutlined />}
              onClick={() => onOpenLogin?.()}
              style={{ paddingInline: 4 }}
            >
              登录
            </Button>
          </Flex>
        )}
      </div>
      <Modal
        title="系统设置"
        open={settingsOpen}
        okText="保存"
        cancelText="取消"
        onOk={saveSettings}
        onCancel={() => setSettingsOpen(false)}
        destroyOnClose
      >
        <Flex vertical gap="middle" style={{ marginTop: 8 }}>
          {isLoggedIn && user?.email ? (
            <div>
              <Text type="secondary" style={{ display: 'block', marginBottom: 4 }}>
                当前账号
              </Text>
              <Text>{user.email}</Text>
            </div>
          ) : null}
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              侧栏显示名（可选，仅本机）
            </Text>
            <Input
              value={nameDraft}
              onChange={(e) => setNameDraft(e.target.value)}
              placeholder={accountLabel}
              maxLength={32}
            />
          </div>
        </Flex>
      </Modal>
    </Flex>
  );
};
