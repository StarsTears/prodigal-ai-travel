import { defineConfig } from 'umi';

export default defineConfig({
  title: 'AI 旅游助手',
  /**
   * 生产环境为 JS/CSS 等增加内容哈希，避免「只更新了部分静态资源」或「入口页被缓存」时出现
   * Loading chunk xxx failed（index 引用旧 chunk 名而磁盘上已是新构建）。
   */
  hash: true,
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
