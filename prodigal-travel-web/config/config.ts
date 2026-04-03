import { defineConfig } from 'umi';

export default defineConfig({
  title: 'AI 旅游助手',
  favicons: ['/logo.svg'],
  plugins: ['@umijs/plugins/dist/antd'],
  antd: {
    configProvider: {},
    theme: {
      token: {
        colorPrimary: '#1677ff',
        borderRadius: 8,
      },
      components: {
        Layout: {
          headerBg: '#ffffff',
          headerColor: 'rgba(0, 0, 0, 0.88)',
        },
      },
    },
  },
  npmClient: 'npm',
  /**
   * 与 `prodigal-travel` 的 `server.servlet.context-path=/api` 对齐：
   * 浏览器请求 `/api/**` 原样转发到后端，不做 pathRewrite。
   * 端口以 `application.yml` 为准（默认 8088）。
   */
  proxy: {
    '/api': {
      target: 'http://localhost:8088',
      changeOrigin: true,
    },
  },
});
