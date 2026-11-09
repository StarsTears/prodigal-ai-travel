import {
  AppstoreOutlined,
  CompassOutlined,
  HomeOutlined,
  MenuOutlined,
  PlusOutlined,
  RocketOutlined,
} from '@ant-design/icons';
import { Drawer, Grid, Modal, message } from 'antd';
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
import type { TravelSearchMode } from '@/types';
import { type ChatMode, persistChatMode } from '@/utils/chatMode';

export interface ChatWorkspaceProps {
  mode: StreamChatKind;
  /** 顶栏模式名 */
  title: string;
  /** 空态说明 */
  emptyHint: string;
  /** 是否显示右侧「快速开始」面板（旅游助手专用） */
  showToolPanel: boolean;
}

export const ChatWorkspace: React.FC<ChatWorkspaceProps> = ({
  mode,
  title,
  emptyHint,
  showToolPanel,
}) => {
  const { token } = useAuth();
  const screens = Grid.useBreakpoint();
  const isWide = screens.lg === true;
  const isManus = mode === 'manus';

  const [toolsOpen, setToolsOpen] = useState(true);
  const [historyDrawerOpen, setHistoryDrawerOpen] = useState(false);
  const [toolsDrawerOpen, setToolsDrawerOpen] = useState(false);
  const [authModalOpen, setAuthModalOpen] = useState(false);
  const [searchMode, setSearchMode] = useState<TravelSearchMode>('web');

  const {
    conversations,
    activeId,
    messages,
    sending,
    listLoading,
    newChat,
    selectChat,
    deleteChat,
    renameChat,
    sendUserMessage,
    stopGenerating,
    geoState,
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
      void sendUserMessage(t, searchMode);
    },
    [token, sendUserMessage, searchMode]
  );

  useEffect(() => {
    if (token && authModalOpen) {
      setAuthModalOpen(false);
    }
  }, [token, authModalOpen]);

  const switchMode = useCallback(
    (next: ChatMode) => {
      if (next === mode) return;
      persistChatMode(next);
      history.push(next === 'manus' ? '/chat/manus' : '/chat/travel');
    },
    [mode]
  );

  const goHome = useCallback(() => {
    history.push('/entry');
  }, []);

  const handleNewChat = useCallback(() => {
    newChat();
    setHistoryDrawerOpen(false);
  }, [newChat]);

  const openLogin = useCallback(() => setAuthModalOpen(true), []);

  const sidebar = (
    <Sidebar
      conversations={conversations}
      activeId={activeId}
      listLoading={Boolean(token) && listLoading}
      showSessionBlock={!isWide}
      onNew={handleNewChat}
      onSelect={(id) => {
        selectChat(id);
        setHistoryDrawerOpen(false);
      }}
      onDelete={deleteChat}
      onRename={renameChat}
      onOpenLogin={openLogin}
    />
  );

  const toolPanel = (
    <ToolPanel
      mode={mode}
      searchMode={searchMode}
      showQuickPrompts={messages.length > 0}
      onQuickPrompt={(t) => {
        send(t);
        setToolsDrawerOpen(false);
      }}
    />
  );

  const topbar = (
    <header className="chat-topbar">
      <div className="chat-topbar__group">
        <span className="brand-mark">
          <img src="/logo.svg" alt="" width={22} height={22} />
        </span>
        <span className="chat-topbar__title">{title}</span>
      </div>

      <div className="chat-topbar__group">
        <div className="mode-switch" role="group" aria-label="切换工作模式">
          <button
            type="button"
            className={`mode-switch__item ${!isManus ? 'mode-switch__item--active' : ''}`}
            onClick={() => switchMode('travel')}
          >
            <CompassOutlined />
            {isWide ? '旅游助手' : '助手'}
          </button>
          <button
            type="button"
            className={`mode-switch__item mode-switch__item--violet ${
              isManus ? 'mode-switch__item--active' : ''
            }`}
            onClick={() => switchMode('manus')}
          >
            <RocketOutlined />
            {isWide ? '超级智能体' : '智能体'}
          </button>
        </div>
      </div>

      <div className="chat-topbar__group chat-topbar__group--right">
        {/* 宽屏的新建入口已在侧栏，顶栏不重复；窄屏侧栏收进抽屉时再显示 */}
        {!isWide ? (
          <button type="button" className="icon-btn" onClick={handleNewChat}>
            <PlusOutlined /> 新对话
          </button>
        ) : null}
        {isWide && showToolPanel ? (
          <button
            type="button"
            className="icon-btn"
            onClick={() => setToolsOpen((v) => !v)}
          >
            <AppstoreOutlined />
            {toolsOpen ? '收起示例' : '示例问题'}
          </button>
        ) : null}
        {!isWide ? (
          <button
            type="button"
            className="icon-btn"
            onClick={() => setHistoryDrawerOpen(true)}
          >
            <MenuOutlined /> 历史
          </button>
        ) : null}
        {!isWide && showToolPanel ? (
          <button
            type="button"
            className="icon-btn"
            onClick={() => setToolsDrawerOpen(true)}
          >
            <AppstoreOutlined /> 示例
          </button>
        ) : null}
        <button
          type="button"
          className="icon-btn icon-btn--square"
          onClick={goHome}
          title="返回首页"
          aria-label="返回首页"
        >
          <HomeOutlined />
        </button>
        {isWide ? <UserSessionBlock variant="toolbar" onOpenLogin={openLogin} /> : null}
      </div>
    </header>
  );

  return (
    <div className="app-shell">
      {topbar}

      <div className="chat-layout">
        {isWide ? <aside className="chat-side">{sidebar}</aside> : null}

        <main className="chat-main">
          <div className="chat-scroll-host">
            <MessageList
              messages={messages}
              bubbleVariant={mode}
              emptyHint={emptyHint}
              onQuickPrompt={send}
            />
          </div>
          <div className="chat-dock">
            <MessageInput
              disabled={sending}
              onSend={send}
              onStop={stopGenerating}
              placeholder={
                isManus
                  ? '描述一个较复杂的任务目标，或点一个示例问题开始…'
                  : '描述你的旅行问题，或点一个示例问题开始…'
              }
              showSearchModeSwitch={!isManus}
              searchMode={searchMode}
              onSearchModeChange={setSearchMode}
              loginRequired={!token}
              onOpenLogin={openLogin}
              geoStatus={isManus ? undefined : geoState.status}
            />
            <div className="chat-disclaimer">
              AI 生成内容仅供参考，请自行核实天气、票价与开放时间等信息
            </div>
          </div>
        </main>

        {isWide && showToolPanel && toolsOpen ? (
          <aside className="chat-tools">{toolPanel}</aside>
        ) : null}
      </div>

      {!isWide ? (
        <Drawer
          title="历史对话"
          placement="left"
          width={280}
          open={historyDrawerOpen}
          onClose={() => setHistoryDrawerOpen(false)}
          styles={{ body: { padding: 0, height: '100%' } }}
        >
          {sidebar}
        </Drawer>
      ) : null}

      {!isWide && showToolPanel ? (
        <Drawer
          title="快速开始"
          placement="right"
          width={Math.min(360, typeof window !== 'undefined' ? window.innerWidth - 24 : 360)}
          open={toolsDrawerOpen}
          onClose={() => setToolsDrawerOpen(false)}
        >
          {toolPanel}
        </Drawer>
      ) : null}

      <Modal
        title={null}
        open={authModalOpen}
        onCancel={() => setAuthModalOpen(false)}
        footer={null}
        width={440}
        destroyOnHidden
        centered
        styles={{ content: { paddingTop: 20 } }}
      >
        <AuthCard
          embedded
          onLoginSuccess={() => setAuthModalOpen(false)}
          subtitle="登录后即可发送消息并与 AI 对话"
        />
      </Modal>
    </div>
  );
};
