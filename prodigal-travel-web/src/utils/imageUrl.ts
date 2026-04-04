/** 路径或完整 URL 上常见的图片扩展（含查询串前的后缀） */
const IMAGE_PATH_EXT = /\.(jpe?g|png|gif|webp|svg|bmp|avif)(?:\?|#|$)/i;

/**
 * 判断 href 是否指向图片资源（用于将 Markdown 链接渲染为预览图）。
 * 覆盖带后缀的直链，以及 Pexels 等已知仅返回图片的 CDN。
 */
export function isImageUrl(href: string): boolean {
  if (!href || typeof href !== 'string') return false;
  const trimmed = href.trim();
  try {
    const u = new URL(trimmed);
    if (u.protocol !== 'http:' && u.protocol !== 'https:') return false;
    const path = u.pathname;
    if (IMAGE_PATH_EXT.test(path)) return true;
    if (/images\.pexels\.com$/i.test(u.hostname)) return true;
    return false;
  } catch {
    return false;
  }
}
