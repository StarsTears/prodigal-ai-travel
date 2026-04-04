import {
  DeleteOutlined,
  LoginOutlined,
  LogoutOutlined,
  MoreOutlined,
  PlusOutlined,
  SearchOutlined,
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
  Menu,
  Modal,
  Popconfirm,
  Typography,
  message,
  theme,
} from 'antd';
import type { MenuProps } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import type { ConversationRecord } from '@/types';

const DISPLAY_NAME_KEY = 'prodigal-travel-display-name';

export interface SidebarProps {
  conversations: ConversationRecord[];
  activeId: string | null;
  search: string;
  onSearchChange: (v: string) => void;
  onNew: () => void;
  onSelect: (id: string) => void;
  onDelete: (id: string) => void;
  /** 未登录时点击打开登录弹窗 */
  onOpenLogin?: () => void;
}

const { Text } = Typography;

export const Sidebar: React.FC<SidebarProps> = ({
  conversations,
  activeId,
  search,
  onSearchChange,
  onNew,
  onSelect,
  onDelete,
  onOpenLogin,
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

  return (
    <Flex
      vertical
      gap="small"
      style={{
        height: '100%',
        minHeight: 0,
        padding: 12,
        boxSizing: 'border-box',
      }}
    >
      <Flex align="center" gap={10} style={{ flexShrink: 0, paddingBottom: 4 }}>
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
      <div style={{ flexShrink: 0 }}>
        <Button type="primary" icon={<PlusOutlined />} block onClick={onNew}>
          新对话
        </Button>
        <Input
          allowClear
          style={{ marginTop: 8 }}
          value={search}
          onChange={(e) => onSearchChange(e.target.value)}
          placeholder="搜索历史…"
          prefix={<SearchOutlined />}
        />
      </div>
      <div style={{ flex: 1, minHeight: 0, overflow: 'auto' }}>
        {conversations.length === 0 ? (
          <Empty description={<Text type="secondary">暂无匹配记录</Text>} />
        ) : (
          <Menu
            mode="inline"
            style={{ borderInlineEnd: 'none' }}
            selectedKeys={activeId ? [activeId] : []}
            onClick={({ key }) => onSelect(String(key))}
            items={conversations.map((c) => ({
              key: c.id,
              label: (
                <Typography.Text ellipsis={{ tooltip: c.title }}>
                  {c.title}
                </Typography.Text>
              ),
              extra: (
                <Popconfirm
                  title="删除此对话？"
                  okText="删除"
                  cancelText="取消"
                  onConfirm={(e) => {
                    e?.stopPropagation?.();
                    onDelete(c.id);
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
              ),
            }))}
          />
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
