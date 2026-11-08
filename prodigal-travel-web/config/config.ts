import { defineConfig } from 'umi';

export default defineConfig({
  title: 'AI 旅游助手',
  /**
   * 生产环境为 JS/CSS 等增加内容哈希，避免「只更新了部分静态资源」或「入口页被缓存」时出现
   * Loading chunk xxx failed（index 引用旧 chunk 名而磁盘上已是新构建）。
   * 带哈希的资源在 nginx 侧使用 1 年 immutable 缓存。
   */
  hash: true,
  favicons: ['/logo.svg'],
  plugins: [
    '@umijs/plugins/dist/antd',
    // 构建期把首屏骨架内联进 index.html，主包下载完成前不再是一片纯色黑屏
    './config/bootSkeleton.ts',
  ],
  /**
   * 刻意在入口只保留一个 JS 主包：Umi 会把「初始 chunk」以阻塞 <script> 注入 <head>，
   * 强行拆 vendor 会让 antd 变成 640KB 的 head 阻塞脚本，首屏总下载量
   * 从 232KB(gzip) 反增到 371KB，并把骨架屏挡在脚本之后。
   * 真正的瘦身靠：nginx gzip + 路由级分包（Umi 默认）+ Markdown 按需加载。
   */
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
