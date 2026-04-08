import React from 'react';
import { ChatWorkspace } from '@/components/chat/ChatWorkspace';

const ManusChatPage: React.FC = () => (
  <ChatWorkspace
    mode="manus"
    title="超级智能体"
    emptyHint="服务端按 Agent 循环逐步推送（工具结果为步骤日志，模型最终说明为综合回复）。登录后即可提问；会话列表仅保存在本机，与旅游助手后端会话无关。"
    showToolPanel={false}
    alternateHref="/chat/travel"
    alternateLabel="AI 旅游助手"
  />
);

export default ManusChatPage;
