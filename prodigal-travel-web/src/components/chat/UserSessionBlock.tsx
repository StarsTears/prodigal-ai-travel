import {
  DownOutlined,
  EnvironmentOutlined,
  LoginOutlined,
  LogoutOutlined,
  SettingOutlined,
  UserDeleteOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Button,
  Divider,
  Dropdown,
  Flex,
  Input,
  Modal,
  Switch,
  Typography,
  message,
} from 'antd';
import type { MenuProps } from 'antd';
import React, { useEffect, useMemo, useState } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import {
  ASSISTANT_AVATAR_ALT,
  ASSISTANT_PORTRAIT_URL,
  GEO_STATUS_TEXT,
  getGeoState,
  readAutoLocate,
  readDisplayName,
  requestGeolocation,
  setGeoState as publishGeoState,
  subscribeGeo,
  writeAutoLocate,
  writeDisplayName,
  type GeoState,
} from '@/utils/preferences';

const { Text } = Typography;

export interface UserSessionBlockProps {
  /** 访客点击登录 */
  onOpenLogin?: () => void;
  /** toolbar：顶栏紧凑胶囊；sidebar：侧栏底部全宽 */
  variant?: 'sidebar' | 'toolbar';
}

export const UserSessionBlock: React.FC<UserSessionBlockProps> = ({
  onOpenLogin,
  variant = 'sidebar',
}) => {
  const compact = variant === 'toolbar';
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
  const [autoLocate, setAutoLocate] = useState(true);
  const [geo, setGeo] = useState<GeoState>(getGeoState);

  useEffect(() => {
    setLocalAlias(readDisplayName());
    setAutoLocate(readAutoLocate());
    setGeo(getGeoState());
    return subscribeGeo(setGeo);
  }, []);

  const displayName = localAlias || (isLoggedIn ? accountLabel : '未登录');

  const openSettings = () => {
    setNameDraft(localAlias);
    setAutoLocate(readAutoLocate());
    setGeo(getGeoState());
    setSettingsOpen(true);
  };

  const saveSettings = () => {
    const next = nameDraft.trim();
    setLocalAlias(next);
    writeDisplayName(next);
    writeAutoLocate(autoLocate);
    setSettingsOpen(false);
  };

  /** 位置开关即时生效，避免「改了设置没保存」的困惑。 */
  const handleAutoLocateChange = (enabled: boolean) => {
    setAutoLocate(enabled);
    writeAutoLocate(enabled);
    if (enabled) {
      void requestGeolocation({ force: true });
    } else {
      publishGeoState({ status: 'off' });
    }
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
      label: '偏好设置',
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

  const shellStyle: React.CSSProperties = {
    width: compact ? undefined : '100%',
    padding: compact ? '5px 10px' : '6px 8px',
    borderRadius: 10,
    border: compact ? '1px solid var(--border)' : '1px solid transparent',
    background: compact ? 'var(--surface-2)' : 'transparent',
    cursor: isLoggedIn ? 'pointer' : 'default',
    transition: 'background 0.16s ease, border-color 0.16s ease',
  };

  const avatar = (
    <Avatar
      size={compact ? 26 : 28}
      style={{
        flexShrink: 0,
        background: isLoggedIn
          ? 'linear-gradient(135deg, #2563eb, #7c3aed)'
          : 'var(--surface-3)',
        color: '#fff',
      }}
    >
      {isLoggedIn ? displayName.slice(0, 1).toUpperCase() : '访'}
    </Avatar>
  );

  return (
    <>
      <Flex align="center" style={{ flexShrink: 0, maxWidth: compact ? 220 : undefined }}>
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
              style={shellStyle}
              onMouseEnter={(e) => {
                e.currentTarget.style.background = 'var(--surface-3)';
              }}
              onMouseLeave={(e) => {
                e.currentTarget.style.background = compact
                  ? 'var(--surface-2)'
                  : 'transparent';
              }}
              aria-label="用户菜单"
            >
              {avatar}
              <Typography.Text
                strong
                ellipsis
                style={{
                  flex: 1,
                  minWidth: 0,
                  maxWidth: compact ? 120 : undefined,
                  fontSize: 13,
                  color: 'var(--text)',
                }}
              >
                {displayName}
              </Typography.Text>
              <DownOutlined style={{ color: 'var(--faint)', fontSize: 11, flexShrink: 0 }} />
            </Flex>
          </Dropdown>
        ) : (
          <Flex align="center" gap="small" style={shellStyle}>
            {avatar}
            <Typography.Text
              ellipsis
              style={{
                flex: 1,
                minWidth: 0,
                maxWidth: compact ? 72 : undefined,
                fontSize: 13,
                color: 'var(--muted)',
              }}
            >
              {displayName}
            </Typography.Text>
            <Button
              type="primary"
              size="small"
              icon={<LoginOutlined />}
              onClick={() => onOpenLogin?.()}
              style={{ paddingInline: 8 }}
            >
              登录
            </Button>
          </Flex>
        )}
      </Flex>

      <Modal
        title="偏好设置"
        open={settingsOpen}
        okText="保存"
        cancelText="取消"
        onOk={saveSettings}
        onCancel={() => setSettingsOpen(false)}
        width={460}
        destroyOnHidden
        centered
      >
        <Flex vertical gap={16} style={{ marginTop: 12 }}>
          <div>
            <Text type="secondary" className="pref-label">账号</Text>
            {isLoggedIn && user?.email ? (
              <Text>{user.email}</Text>
            ) : (
              <Text type="secondary">未登录 · 登录后消息会保存到云端会话</Text>
            )}
          </div>

          <Divider style={{ margin: 0 }} />

          <div>
            <Text type="secondary" className="pref-label">显示名</Text>
            <Input
              value={nameDraft}
              onChange={(e) => setNameDraft(e.target.value)}
              placeholder={accountLabel}
              maxLength={32}
            />
            <Text type="secondary" className="pref-help">
              仅保存在本机，用于右上角用户菜单与头像首字母。
            </Text>
          </div>

          <Divider style={{ margin: 0 }} />

          <div>
            <Text type="secondary" className="pref-label">助手形象</Text>
            <Flex align="center" gap={12}>
              <img
                className="pref-avatar"
                src={ASSISTANT_PORTRAIT_URL}
                alt={ASSISTANT_AVATAR_ALT}
              />
              <div style={{ minWidth: 0 }}>
                <Text strong>{ASSISTANT_AVATAR_ALT}</Text>
                <div>
                  <Text type="secondary" className="pref-help">
                    AI 旅游助手与超级智能体统一使用此形象。
                  </Text>
                </div>
              </div>
            </Flex>
          </div>

          <Divider style={{ margin: 0 }} />

          <div>
            <Flex align="center" justify="space-between" gap={16}>
              <div style={{ minWidth: 0 }}>
                <Text strong>默认获取当前位置</Text>
                <div>
                  <Text type="secondary" className="pref-help">
                    用于天气与附近推荐；关闭后由后端按 IP 粗略定位。
                  </Text>
                </div>
              </div>
              <Switch checked={autoLocate} onChange={handleAutoLocateChange} />
            </Flex>
            <Flex align="center" justify="space-between" gap={12} style={{ marginTop: 12 }}>
              <Text type="secondary" className="pref-help">
                <EnvironmentOutlined />　{GEO_STATUS_TEXT[geo.status]}
              </Text>
              <Button
                size="small"
                icon={<EnvironmentOutlined />}
                disabled={!autoLocate || geo.status === 'pending'}
                onClick={() => void requestGeolocation({ force: true })}
              >
                重新获取
              </Button>
            </Flex>
          </div>
        </Flex>
      </Modal>
    </>
  );
};
