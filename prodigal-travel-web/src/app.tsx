import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import React from 'react';
import { AppErrorBoundary } from '@/components/AppErrorBoundary';
import { AuthProvider } from '@/contexts/AuthContext';
import './global.css';

/**
 * 产品为深色工作台；Umi antd 插件在外层注册的是浅色主题，
 * 这里用 `inherit: false` 重置并统一为深色算法，避免弹窗 / 表单 / 下拉菜单
 * 出现「白卡片贴在深色页面上」的割裂感。
 */
export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider
      locale={zhCN}
      theme={{
        algorithm: theme.darkAlgorithm,
        inherit: false,
        token: {
          colorPrimary: '#4f8cff',
          colorInfo: '#4f8cff',
          colorBgBase: '#080d1a',
          colorTextBase: '#e8eefb',
          borderRadius: 10,
          fontFamily:
            "-apple-system, BlinkMacSystemFont, 'Segoe UI', 'PingFang SC', 'Microsoft YaHei', sans-serif",
        },
      }}
    >
      <AppErrorBoundary>
        <AuthProvider>{container}</AuthProvider>
      </AppErrorBoundary>
    </ConfigProvider>
  );
}
