import { ConfigProvider } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import React from 'react';
import { AppErrorBoundary } from '@/components/AppErrorBoundary';
import { AuthProvider } from '@/contexts/AuthContext';
import './global.css';

export function rootContainer(container: React.ReactNode) {
  return (
    <ConfigProvider locale={zhCN}>
      <AppErrorBoundary>
        <AuthProvider>{container}</AuthProvider>
      </AppErrorBoundary>
    </ConfigProvider>
  );
}
