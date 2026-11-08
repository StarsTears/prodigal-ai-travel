/**
 * 首屏骨架（构建期插件）
 *
 * Umi 是纯客户端渲染：nginx 返回 index.html 后，浏览器要先下载 ~200KB(gzip) 主包、
 * 再执行 React 挂载，这 2~4 秒里 `#root` 是空的，用户只看到一片深色空白，
 * 容易被当成站点故障。这里把骨架直接内联进 index.html：
 *   - 纯 HTML + 内联 CSS，不依赖任何外部资源，随首个响应一起到达；
 *   - 结构与聊天页一致（顶栏 / 会话栏 / 欢迎区 / 输入区 / 右侧说明），
 *     挂载完成后无跳变；
 *   - 内联脚本监听 `#root`，React 一渲染就移除；异常时 30s 兜底自动移除。
 *
 * 注意：本文件是 Umi 构建期插件，不要改成运行时模块。
 */

const STYLE = `
#boot-skeleton{position:fixed;inset:0;z-index:9999;overflow:hidden;background:#070b16;color:#e8eefb;
  font:14px/1.5 -apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;
  display:flex;flex-direction:column}
#boot-skeleton *{box-sizing:border-box}
.bs-sh{background:linear-gradient(100deg,rgba(148,163,184,.09) 30%,rgba(148,163,184,.19) 48%,rgba(148,163,184,.09) 66%);
  background-size:240% 100%;animation:bs-shine 1.5s ease-in-out infinite;border-radius:8px}
@keyframes bs-shine{0%{background-position:100% 0}100%{background-position:0 0}}
.bs-top{flex:0 0 58px;display:flex;align-items:center;gap:12px;padding:0 18px;
  border-bottom:1px solid rgba(148,163,184,.13)}
.bs-logo{width:28px;height:28px;flex:0 0 28px;border-radius:9px;
  background:linear-gradient(135deg,rgba(79,140,255,.4),rgba(139,92,246,.3))}
.bs-bar{height:12px;display:block}
.bs-w110{width:110px}.bs-w150{width:150px}.bs-w200{width:200px}.bs-w260{width:260px}
.bs-grow{flex:1}
.bs-pill{width:70px;height:28px;border-radius:14px}
.bs-avatar{width:30px;height:30px;border-radius:50%}
.bs-body{flex:1;min-height:0;display:flex;gap:0}
.bs-side{width:252px;flex:0 0 252px;padding:16px 14px;border-right:1px solid rgba(148,163,184,.13);
  display:flex;flex-direction:column;gap:12px}
.bs-btn{height:38px;border-radius:11px}
.bs-search{height:32px;border-radius:9px}
.bs-row{height:34px;border-radius:9px}
.bs-main{flex:1;min-width:0;display:flex;flex-direction:column;align-items:center;
  justify-content:center;padding:20px 36px 0}
.bs-hero{width:100%;max-width:660px;display:flex;flex-direction:column;align-items:center;gap:14px}
.bs-mascot{width:76px;height:76px;border-radius:50%;
  background:radial-gradient(circle at 50% 40%,rgba(79,140,255,.32),rgba(19,28,49,.9))}
.bs-title{width:230px;height:20px}
.bs-sub{width:330px;height:12px}
.bs-cards{width:100%;display:grid;grid-template-columns:repeat(2,minmax(0,1fr));gap:12px;margin-top:18px}
.bs-card{height:74px;border-radius:12px}
.bs-composer{width:100%;max-width:760px;height:104px;margin-top:auto;border-radius:14px;
  border:1px solid rgba(148,163,184,.15)}
.bs-disclaimer{width:190px;height:9px;margin:8px auto 10px}
.bs-panel{width:270px;flex:0 0 270px;padding:20px 16px;border-left:1px solid rgba(148,163,184,.13);
  display:flex;flex-direction:column;gap:14px}
.bs-note{height:82px;border-radius:11px}
.bs-status{position:absolute;left:50%;bottom:26px;transform:translateX(-50%);
  display:flex;align-items:center;gap:9px;font-size:12px;color:#9aabc4;white-space:nowrap}
.bs-dot{width:7px;height:7px;border-radius:50%;background:#4f8cff;animation:bs-pulse 1.2s ease-in-out infinite}
@keyframes bs-pulse{0%,100%{opacity:.35;transform:scale(.8)}50%{opacity:1;transform:scale(1.15)}}
@media (max-width:1180px){.bs-panel{display:none}}
@media (max-width:900px){.bs-side{display:none}.bs-main{padding:16px 18px 0}}
@media (max-width:640px){.bs-top{padding:0 12px}.bs-cards{gap:8px}.bs-card{height:62px}
  .bs-composer{height:92px}.bs-w110,.bs-w150,.bs-w200{width:80px}}
@media (prefers-reduced-motion:reduce){.bs-sh,.bs-dot{animation:none}}
`;

const MARKUP = `
<div id="boot-skeleton" role="status" aria-live="polite" aria-label="正在加载 AI 旅游助手">
  <div class="bs-top">
    <span class="bs-logo"></span>
    <span class="bs-bar bs-sh bs-w110"></span>
    <span class="bs-grow"></span>
    <span class="bs-pill bs-sh"></span>
    <span class="bs-pill bs-sh"></span>
    <span class="bs-avatar bs-sh"></span>
  </div>
  <div class="bs-body">
    <div class="bs-side">
      <div class="bs-btn bs-sh"></div>
      <div class="bs-search bs-sh"></div>
      <div class="bs-row bs-sh"></div>
      <div class="bs-row bs-sh"></div>
      <div class="bs-row bs-sh"></div>
      <div class="bs-row bs-sh"></div>
    </div>
    <div class="bs-main">
      <div class="bs-hero">
        <div class="bs-mascot bs-sh"></div>
        <div class="bs-title bs-sh"></div>
        <div class="bs-sub bs-sh"></div>
        <div class="bs-cards">
          <div class="bs-card bs-sh"></div><div class="bs-card bs-sh"></div>
          <div class="bs-card bs-sh"></div><div class="bs-card bs-sh"></div>
        </div>
      </div>
      <div class="bs-composer bs-sh"></div>
      <div class="bs-disclaimer bs-sh"></div>
    </div>
    <div class="bs-panel">
      <div class="bs-bar bs-sh bs-w150"></div>
      <div class="bs-note bs-sh"></div>
      <div class="bs-row bs-sh"></div>
      <div class="bs-row bs-sh"></div>
      <div class="bs-row bs-sh"></div>
      <div class="bs-row bs-sh"></div>
    </div>
  </div>
  <div class="bs-status"><span class="bs-dot"></span><span class="bs-status-text">正在加载 AI 旅游助手…</span></div>
</div>
`;

/** 主包执行前挂在 #root 上观察，React 首次渲染后立即移除骨架。 */
const WATCHER = `
(function(){
  var root=document.getElementById('root');
  var sk=document.getElementById('boot-skeleton');
  if(!root||!sk){return;}
  var removed=false;
  function remove(){
    if(removed){return;}
    removed=true;
    if(sk.parentNode){sk.parentNode.removeChild(sk);}
    var st=document.getElementById('boot-skeleton-style');
    if(st&&st.parentNode){st.parentNode.removeChild(st);}
  }
  if(window.MutationObserver){
    var mo=new MutationObserver(function(){
      if(root.childNodes.length){mo.disconnect();remove();}
    });
    mo.observe(root,{childList:true});
    if(root.childNodes.length){mo.disconnect();remove();}
  }
  setTimeout(function(){
    var t=sk.querySelector('.bs-status-text');
    if(t&&!removed){t.textContent='网络较慢，仍在加载，请稍候…';}
  },12000);
  setTimeout(remove,30000);
})();
`;

export default function bootSkeletonPlugin(api: any) {
  api.modifyHTML(($: any) => {
    $('head').append('<style id="boot-skeleton-style">' + STYLE + '</style>');
    const root = $('#root');
    if (root.length) {
      root.after(MARKUP + '<script>' + WATCHER + '</script>');
    }
    return $;
  });
}
