import {
  DeleteOutlined,
  PlusOutlined,
  SearchOutlined,
  SettingOutlined,
} from '@ant-design/icons';
import {
  Avatar,
  Button,
  Empty,
  Flex,
  Input,
  Menu,
  Modal,
  Popconfirm,
  Typography,
} from 'antd';
import React, { useEffect, useState } from 'react';
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
}) => {
  const [displayName, setDisplayName] = useState('访客');
  const [settingsOpen, setSettingsOpen] = useState(false);
  const [nameDraft, setNameDraft] = useState('');

  useEffect(() => {
    try {
      const v = localStorage.getItem(DISPLAY_NAME_KEY);
      if (v?.trim()) setDisplayName(v.trim());
    } catch {
      /* ignore */
    }
  }, []);

  const openSettings = () => {
    setNameDraft(displayName);
    setSettingsOpen(true);
  };

  const saveSettings = () => {
    const next = nameDraft.trim() || '访客';
    setDisplayName(next);
    try {
      localStorage.setItem(DISPLAY_NAME_KEY, next);
    } catch {
      /* ignore */
    }
    setSettingsOpen(false);
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
      <Flex
        align="center"
        gap="small"
        style={{
          flexShrink: 0,
          marginTop: 'auto',
          paddingTop: 12,
          borderTop: '1px solid rgba(0, 0, 0, 0.06)',
        }}
      >
        <Avatar size="small" style={{ flexShrink: 0 }}>
          {displayName.slice(0, 1).toUpperCase()}
        </Avatar>
        <Typography.Text ellipsis style={{ flex: 1, minWidth: 0 }}>
          {displayName}
        </Typography.Text>
        <Button
          type="text"
          size="small"
          icon={<SettingOutlined />}
          aria-label="系统设置"
          onClick={openSettings}
        />
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
          <div>
            <Text type="secondary" style={{ display: 'block', marginBottom: 6 }}>
              显示名称（仅保存在本机浏览器）
            </Text>
            <Input
              value={nameDraft}
              onChange={(e) => setNameDraft(e.target.value)}
              placeholder="访客"
              maxLength={32}
            />
          </div>
        </Flex>
      </Modal>
    </Flex>
  );
};
