import {
  LoginOutlined,
  LogoutOutlined,
  MoreOutlined,
  SettingOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Button,
  Dropdown,
  Flex,
  Input,
  Modal,
  Typography,
  message,
  theme,
} from 'antd';
import type { MenuProps } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';

const DISPLAY_NAME_KEY = 'prodigal-travel-display-name';

const { Text } = Typography;

export interface UserSessionBlockProps {
  /** 访客点击登录 */
  onOpenLogin?: () => void;
  /** 旅游助手侧栏底部：全宽、无描边 */
  variant?: 'sidebar' | 'toolbar';
}

export const UserSessionBlock: React.FC<UserSessionBlockProps> = ({
  onOpenLogin,
  variant = 'sidebar',
}) => {
  const compact = variant === 'toolbar';
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

  const avatarSize = compact ? 26 : 28;
  const panelBg = compact ? themeToken.colorBgContainer : 'transparent';
  const panelBorder = compact
    ? '1px solid rgba(0, 0, 0, 0.06)'
    : '1px solid transparent';
  const textColor = compact ? undefined : 'rgba(226, 232, 240, 0.92)';
  const iconColor = compact
    ? themeToken.colorTextTertiary
    : 'rgba(148, 163, 184, 0.95)';

  return (
    <>
      <Flex
        align="center"
        gap={compact ? 8 : 10}
        style={{
          flexShrink: 0,
          maxWidth: compact ? 260 : undefined,
        }}
      >
        {isLoggedIn ? (
          <Dropdown
            menu={{ items: profileMenuItems, onClick: onProfileMenuClick }}
            trigger={['click']}
            placement={compact ? 'bottomRight' : 'topRight'}
            overlayClassName="prodigal-profile-dropdown"
            getPopupContainer={() => document.body}
          >
            <Flex
              align="center"
              gap={compact ? 8 : 10}
              style={{
                width: compact ? undefined : '100%',
                padding: compact ? '6px 8px' : '8px 10px',
                borderRadius: themeToken.borderRadiusLG,
                cursor: 'pointer',
                transition: 'background 0.15s ease',
                ...(compact
                  ? {
                      border: panelBorder,
                      background: panelBg,
                    }
                  : {}),
              }}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = compact
                  ? themeToken.colorFillTertiary
                  : 'rgba(148, 163, 184, 0.12)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = compact
                  ? panelBg
                  : 'transparent';
              }}
              aria-label="用户菜单"
            >
              <Avatar size={avatarSize} style={{ flexShrink: 0 }}>
                {displayName.slice(0, 1).toUpperCase()}
              </Avatar>
              <Typography.Text
                strong
                ellipsis
                style={{
                  flex: 1,
                  minWidth: compact ? 72 : 0,
                  maxWidth: compact ? 140 : undefined,
                  fontSize: compact ? 13 : 14,
                  color: textColor,
                }}
              >
                {displayName}
              </Typography.Text>
              <MoreOutlined
                style={{
                  color: iconColor,
                  fontSize: compact ? 14 : 16,
                  flexShrink: 0,
                }}
              />
            </Flex>
          </Dropdown>
        ) : (
          <Flex
            align="center"
            gap="small"
            style={{
              width: compact ? undefined : '100%',
              padding: compact ? '4px 8px' : '4px 0',
              borderRadius: themeToken.borderRadiusLG,
              ...(compact
                ? {
                    border: panelBorder,
                    background: panelBg,
                  }
                : {}),
            }}
          >
            <Avatar size="small" style={{ flexShrink: 0 }}>
              访
            </Avatar>
            <Typography.Text
              ellipsis
              style={{
                flex: 1,
                minWidth: 0,
                maxWidth: compact ? 100 : undefined,
                fontSize: compact ? 13 : 14,
                color: textColor,
              }}
            >
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
      </Flex>
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
    </>
  );
};
