import { ConfigProvider, theme } from 'antd';
import zhCN from 'antd/locale/zh_CN';
import React from 'react';
import { AppErrorBoundary } from '@/components/AppErrorBoundary';
import { AuthProvider } from '@/contexts/AuthContext';
import './global.css';

/**
 * 兜底移除构建期内联的首屏骨架。
 * 正常情况由 index.html 里的 MutationObserver 在 React 首次渲染出 DOM 时移除；
 * 这里只在观察脚本不可用/失效时 8s 后兜底，且必须确认 #root 已有内容，
 * 否则会在「骨架已消失、React 还没提交」之间闪一帧纯色空白。
 */
if (typeof document !== 'undefined') {
  window.setTimeout(() => {
    const root = document.getElementById('root');
    if (root && root.childElementCount > 0) {
      document.getElementById('boot-skeleton')?.remove();
      document.getElementById('boot-skeleton-style')?.remove();
    }
  }, 8000);
}

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
