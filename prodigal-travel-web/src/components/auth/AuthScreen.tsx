import { Flex, theme } from 'antd';
import React from 'react';
import { AuthCard } from '@/components/auth/AuthCard';

/**
 * 全屏认证页：渐变背景 + {@link AuthCard}（独立路由或备用入口时使用）。
 */
export const AuthScreen: React.FC = () => {
  const { token: themeToken } = theme.useToken();
  return (
    <Flex
      align="center"
      justify="center"
      style={{
        width: '100%',
        height: '100%',
        minHeight: 360,
        padding: '24px 16px',
        boxSizing: 'border-box',
        background: `linear-gradient(165deg, ${themeToken.colorFillQuaternary} 0%, ${themeToken.colorBgLayout} 45%, ${themeToken.colorFillTertiary} 100%)`,
      }}
    >
      <AuthCard />
    </Flex>
  );
};
