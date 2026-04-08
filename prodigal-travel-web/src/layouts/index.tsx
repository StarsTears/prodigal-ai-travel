import { Layout } from 'antd';
import React from 'react';
import { Outlet, useLocation } from 'umi';
import { AppHeader } from '@/components/AppHeader';

const { Content } = Layout;

function isChatRoute(pathname: string): boolean {
  return (
    pathname === '/' ||
    pathname === '/entry' ||
    pathname.startsWith('/chat')
  );
}

/**
 * 固定视口高度并禁止整页滚动，子路由内仅聊天消息区自行 overflow:auto。
 * 对话页不展示顶栏，主内容区占满视口（参考 DeepSeek 等全屏对话布局）。
 */
const RootLayout: React.FC = () => {
  const { pathname } = useLocation();
  const showHeader = !isChatRoute(pathname);

  return (
    <Layout
      style={{
        height: '100vh',
        maxHeight: '100vh',
        overflow: 'hidden',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      {showHeader ? <AppHeader /> : null}
      <Content
        style={{
          flex: 1,
          minHeight: 0,
          overflow: 'hidden',
          display: 'flex',
          flexDirection: 'column',
        }}
      >
        <Outlet />
      </Content>
    </Layout>
  );
};

export default RootLayout;
