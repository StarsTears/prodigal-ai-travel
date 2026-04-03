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
  Typography,
} from 'antd';
import React, { useState } from 'react';
import { MessageInput } from '@/components/chat/MessageInput';
import { MessageList } from '@/components/chat/MessageList';
import { Sidebar } from '@/components/chat/Sidebar';
import { ToolPanel } from '@/components/chat/ToolPanel';
import { useChat } from './hooks/useChat';

const { Sider, Content } = Layout;

/** 主对话区最大宽度，两侧留白，风格接近常见 AI 对话产品 */
const CHAT_MAIN_MAX = 920;

/** 与顶栏 AppHeader paddingInline 一致，左右侧栏与视口边缘对称留白 */
const CHAT_PAGE_GUTTER = 24;

const ChatPage: React.FC = () => {
  const screens = Grid.useBreakpoint();
  const isWide = screens.lg === true;

  const [toolsCollapsed, setToolsCollapsed] = useState(false);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [toolsDrawerOpen, setToolsDrawerOpen] = useState(false);

  const {
    conversations,
    activeId,
    messages,
    historySearch,
    setHistorySearch,
    sending,
    newChat,
    selectChat,
    deleteChat,
    sendUserMessage,
  } = useChat();

  const send = (text: string) => void sendUserMessage(text);

  const sidebar = (
    <Sidebar
      conversations={conversations}
      activeId={activeId}
      search={historySearch}
      onSearchChange={setHistorySearch}
      onNew={newChat}
      onSelect={(id) => {
        selectChat(id);
        setHistoryDrawerOpen(false);
      }}
      onDelete={deleteChat}
    />
  );

  const toolPanel = (
    <ToolPanel
      onQuickPrompt={(t) => {
        void sendUserMessage(t);
        setToolsDrawerOpen(false);
      }}
    />
  );

  /** 中间栏：仅消息列表区域滚动，输入框固定在底部不参与滚动 */
  const chatMain = (
    <Flex
      vertical
      gap="middle"
      style={{
        flex: 1,
        minHeight: 0,
        width: '100%',
        maxWidth: CHAT_MAIN_MAX,
        marginInline: 'auto',
        overflow: 'hidden',
      }}
    >
      {!isWide ? (
        <Flex
          justify="space-between"
          align="center"
          gap="small"
          wrap="wrap"
          style={{ flexShrink: 0 }}
        >
          <Button
            type="default"
            icon={<MenuOutlined />}
            onClick={() => setHistoryDrawerOpen(true)}
          >
            对话列表
          </Button>
          <Button
            type="default"
            icon={<AppstoreOutlined />}
            onClick={() => setToolsDrawerOpen(true)}
          >
            示例问题
          </Button>
        </Flex>
      ) : null}
      <div
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'auto',
        }}
      >
        <MessageList messages={messages} />
      </div>
      <div style={{ flexShrink: 0 }}>
        <MessageInput disabled={sending} onSend={send} />
        <Typography.Text
          type="secondary"
          style={{
            display: 'block',
            textAlign: 'center',
            fontSize: 12,
            marginTop: 10,
            lineHeight: 1.6,
            color: 'rgba(0, 0, 0, 0.38)',
          }}
        >
          AI 生成内容仅供参考，请注意辨别真伪
        </Typography.Text>
      </div>
    </Flex>
  );

  if (!isWide) {
    return (
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
            padding: '20px 28px',
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
        <Drawer
          title="示例与说明"
          placement="right"
          width={Math.min(
            360,
            typeof window !== 'undefined' ? window.innerWidth - 24 : 360
          )}
          open={toolsDrawerOpen}
          onClose={() => setToolsDrawerOpen(false)}
          destroyOnClose={false}
        >
          {toolPanel}
        </Drawer>
      </Layout>
    );
  }

  /**
   * 桌面端：横向 Layout 只包含「左 Sider | 中间栏 | 右 Sider」三个子节点。
   * FloatButton 放在中间 Content 内，避免成为第 4 个 flex 子项导致主栏被挤没、输入框消失。
   * 外层 paddingInline 让左右侧栏与视口边缘留白对称（右侧工具栏不再贴边）。
   */
  return (
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
              padding: '20px 32px',
              overflow: 'hidden',
              position: 'relative',
              background: '#f5f6f8',
            }}
          >
            {chatMain}
            <FloatButton
              icon={
                toolsCollapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />
              }
              type="default"
              style={{ bottom: 96, right: 24 }}
              onClick={() => setToolsCollapsed((c) => !c)}
              tooltip={toolsCollapsed ? '展开工具栏' : '收起工具栏'}
            />
          </Content>
        </Layout>
        {!toolsCollapsed ? (
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
  );
};

export default ChatPage;
