import { Flex, Layout, Space, Typography } from 'antd';
import React from 'react';

const { Header } = Layout;

export const AppHeader: React.FC = () => {
  return (
    <Header style={{ paddingInline: 24, borderBottom: '1px solid rgba(0, 0, 0, 0.06)' }}>
      <Flex align="center" wrap="wrap">
        <Space size="middle" align="center" wrap>
          <img
            src="/logo.svg"
            alt=""
            width={32}
            height={32}
            style={{ display: 'block', flexShrink: 0 }}
          />
          <Typography.Text strong>AI 旅游助手</Typography.Text>
          <Typography.Text type="secondary">
            对话 · 天气 · 景点 · 行程
          </Typography.Text>
        </Space>
      </Flex>
    </Header>
  );
};
