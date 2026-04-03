import { Layout } from 'antd';
import React from 'react';
import { Outlet } from 'umi';
import { AppHeader } from '@/components/AppHeader';

const { Content } = Layout;

/**
 * 固定视口高度并禁止整页滚动，子路由内仅聊天消息区自行 overflow:auto。
 */
const RootLayout: React.FC = () => {
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
      <AppHeader />
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
