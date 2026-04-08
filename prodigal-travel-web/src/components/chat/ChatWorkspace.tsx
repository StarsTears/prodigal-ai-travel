import {
  AppstoreOutlined,
  MenuFoldOutlined,
  MenuOutlined,
  MenuUnfoldOutlined,
} from '@ant-design/icons';
import {
  Button,
  Drawer,
  Flex,
  FloatButton,
  Grid,
  Layout,
  Modal,
  Space,
  Typography,
  message,
} from 'antd';
import React, { useCallback, useEffect, useState } from 'react';
import { history } from 'umi';
import { AuthCard } from '@/components/auth/AuthCard';
import { MessageInput } from '@/components/chat/MessageInput';
import { MessageList } from '@/components/chat/MessageList';
import { Sidebar } from '@/components/chat/Sidebar';
import { ToolPanel } from '@/components/chat/ToolPanel';
import { UserSessionBlock } from '@/components/chat/UserSessionBlock';
import { useAuth } from '@/contexts/AuthContext';
import type { StreamChatKind } from '@/pages/chat/hooks/useStreaming';
import { useChat } from '@/pages/chat/hooks/useChat';

const { Sider, Content } = Layout;

const CHAT_MAIN_MAX = 920;
const CHAT_PAGE_GUTTER = 24;

export interface ChatWorkspaceProps {
  mode: StreamChatKind;
  /** 顶栏主标题 */
  title: string;
  /** 空列表提示 */
  emptyHint: string;
  /** 是否显示右侧「示例问题」面板（旅游助手专用） */
  showToolPanel: boolean;
  /** 另一模式的跳转路径 */
  alternateHref: string;
  alternateLabel: string;
}

export const ChatWorkspace: React.FC<ChatWorkspaceProps> = ({
  mode,
  title,
  emptyHint,
  showToolPanel,
  alternateHref,
  alternateLabel,
}) => {
  const { token } = useAuth();
  const screens = Grid.useBreakpoint();
  const isWide = screens.lg === true;

  const [toolsCollapsed, setToolsCollapsed] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [toolsDrawerOpen, setToolsDrawerOpen] = useState(false);
  const [authModalOpen, setAuthModalOpen] = useState(false);

  const {
    conversations,
    activeId,
    messages,
    sending,
    listLoading,
    newChat,
    selectChat,
    deleteChat,
    sendUserMessage,
  } = useChat({ mode });

  const send = useCallback(
    (text: string) => {
      const t = text.trim();
      if (!t) return;
      if (!token) {
        message.warning('请先登录后再发送消息');
        setAuthModalOpen(true);
        return;
      }
      void sendUserMessage(t);
    },
    [token, sendUserMessage]
  );

  useEffect(() => {
    if (token && authModalOpen) {
      setAuthModalOpen(false);
    }
  }, [token, authModalOpen]);

  const loginModal = (
    <Modal
      title={null}
      open={authModalOpen}
      onCancel={() => setAuthModalOpen(false)}
      footer={null}
      width={440}
      destroyOnClose
      centered
      styles={{ content: { paddingTop: 20 } }}
    >
      <AuthCard
        embedded
        onLoginSuccess={() => setAuthModalOpen(false)}
        subtitle="登录后即可发送消息并与 AI 对话"
      />
    </Modal>
  );

  const sidebar = (
    <Sidebar
      conversations={conversations}
      activeId={activeId}
      listLoading={Boolean(token) && listLoading}
      onNew={newChat}
      onSelect={(id) => {
        selectChat(id);
        setHistoryDrawerOpen(false);
      }}
      onDelete={deleteChat}
      onOpenLogin={() => setAuthModalOpen(true)}
    />
  );

  const toolPanel = (
    <ToolPanel
      onQuickPrompt={(t) => {
        send(t);
        setToolsDrawerOpen(false);
      }}
    />
  );

  const isManusSolo = mode === 'manus';

  const headerBar = (
    <Flex
      justify="space-between"
      align="center"
      wrap="wrap"
      gap="middle"
      style={{ flexShrink: 0, width: '100%' }}
    >
      <Flex
        align="center"
        gap="middle"
        wrap="wrap"
        style={{ flex: 1, minWidth: 0 }}
      >
        <Typography.Title level={5} style={{ margin: 0 }}>
          {title}
        </Typography.Title>
        <Space size="small" wrap>
          <Button type="link" style={{ paddingInline: 0 }} onClick={() => history.push('/entry')}>
            返回首页
          </Button>
          <Button type="link" style={{ paddingInline: 0 }} onClick={() => history.push(alternateHref)}>
            {alternateLabel}
          </Button>
        </Space>
      </Flex>
      {isManusSolo ? (
        <UserSessionBlock
          variant="toolbar"
          onOpenLogin={() => setAuthModalOpen(true)}
        />
      ) : null}
    </Flex>
  );

  const chatMain = (
    <Flex
      vertical
      gap={12}
      style={{
        flex: 1,
        minHeight: 0,
        width: '100%',
        maxWidth: CHAT_MAIN_MAX,
        marginInline: 'auto',
        overflow: 'hidden',
      }}
    >
      {headerBar}
      {!isWide && !isManusSolo ? (
        <Flex
          justify="space-between"
          align="center"
          gap="small"
          wrap="wrap"
          style={{ flexShrink: 0 }}
        >
          <Button type="default" icon={<MenuOutlined />} onClick={() => setHistoryDrawerOpen(true)}>
            对话列表
          </Button>
          {showToolPanel ? (
            <Button
              type="default"
              icon={<AppstoreOutlined />}
              onClick={() => setToolsDrawerOpen(true)}
            >
              示例问题
            </Button>
          ) : null}
        </Flex>
      ) : null}
      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
        }}
      >
        <MessageList
          messages={messages}
          bubbleVariant={mode}
          emptyHint={emptyHint}
        />
      </div>
      <div style={{ flexShrink: 0 }}>
        <MessageInput disabled={sending} onSend={send} />
        <Typography.Text
          type="secondary"
          style={{
            display: 'block',
            textAlign: 'center',
            fontSize: 12,
            marginTop: 6,
            lineHeight: 1.5,
            color: 'rgba(0, 0, 0, 0.38)',
          }}
        >
          AI 生成内容仅供参考，请注意辨别真伪
        </Typography.Text>
      </div>
    </Flex>
  );

  /** 超级智能体：无左右侧栏与抽屉，仅顶栏 + 对话区 + 用户块 */
  if (isManusSolo) {
    return (
      <>
        <Layout
          style={{
            flex: 1,
            minHeight: 0,
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <Content
            style={{
              flex: 1,
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
              padding: isWide ? '12px 24px 8px' : '12px 16px 8px',
              overflow: 'hidden',
              background: '#f5f6f8',
            }}
          >
            {chatMain}
          </Content>
        </Layout>
        {loginModal}
      </>
    );
  }

  if (!isWide) {
    return (
      <>
        <Layout
          style={{
            flex: 1,
            minHeight: 0,
            height: '100%',
            display: 'flex',
            flexDirection: 'column',
            overflow: 'hidden',
          }}
        >
          <Content
            style={{
              flex: 1,
              minHeight: 0,
              display: 'flex',
              flexDirection: 'column',
              padding: '12px 16px 8px',
              overflow: 'hidden',
              background: '#f5f6f8',
            }}
          >
            {chatMain}
          </Content>
          <Drawer
            title="历史对话"
            placement="left"
            width={280}
            open={historyDrawerOpen}
            onClose={() => setHistoryDrawerOpen(false)}
            destroyOnClose={false}
            styles={{ body: { padding: 0, height: '100%' } }}
          >
            <div className="chat-sider-inner">{sidebar}</div>
          </Drawer>
          {showToolPanel ? (
            <Drawer
              title="示例与说明"
              placement="right"
              width={Math.min(360, typeof window !== 'undefined' ? window.innerWidth - 24 : 360)}
              open={toolsDrawerOpen}
              onClose={() => setToolsDrawerOpen(false)}
              destroyOnClose={false}
            >
              {toolPanel}
            </Drawer>
          ) : null}
        </Layout>
        {loginModal}
      </>
    );
  }

  return (
    <>
      <div
        style={{
          flex: 1,
          minHeight: 0,
          height: '100%',
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
          paddingInline: CHAT_PAGE_GUTTER,
          boxSizing: 'border-box',
        }}
      >
        <Layout
          hasSider
          style={{
            flex: 1,
            minHeight: 0,
            height: '100%',
            overflow: 'hidden',
          }}
        >
          <Sider
            width={280}
            theme="light"
            style={{
              overflow: 'hidden',
              height: '100%',
              borderRight: '1px solid rgba(0, 0, 0, 0.06)',
            }}
          >
            <div className="chat-sider-inner">{sidebar}</div>
          </Sider>
          <Layout
            style={{
              flex: 1,
              minWidth: 0,
              minHeight: 0,
              height: '100%',
              display: 'flex',
              flexDirection: 'column',
              overflow: 'hidden',
            }}
          >
            <Content
              style={{
                flex: 1,
                minHeight: 0,
                display: 'flex',
                flexDirection: 'column',
                padding: '12px 24px 8px',
                overflow: 'hidden',
                position: 'relative',
                background: '#f5f6f8',
              }}
            >
              {chatMain}
              {showToolPanel ? (
                <FloatButton
                  icon={toolsCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
                  type="default"
                  style={{ bottom: 96, right: 24 }}
                  onClick={() => setToolsCollapsed((c) => !c)}
                  tooltip={toolsCollapsed ? '展开工具栏' : '收起工具栏'}
                />
              ) : null}
            </Content>
          </Layout>
          {showToolPanel && !toolsCollapsed ? (
            <Sider
              width={360}
              theme="light"
              style={{
                overflow: 'hidden',
                height: '100%',
                borderLeft: '1px solid rgba(0, 0, 0, 0.06)',
              }}
            >
              <div
                style={{
                  height: '100%',
                  overflow: 'auto',
                  padding: 12,
                  boxSizing: 'border-box',
                }}
              >
                <ToolPanel onQuickPrompt={send} />
              </div>
            </Sider>
          ) : null}
        </Layout>
      </div>
      {loginModal}
    </>
  );
};
