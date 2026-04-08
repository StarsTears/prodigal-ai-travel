import React from 'react';
import { ChatWorkspace } from '@/components/chat/ChatWorkspace';

const TravelChatPage: React.FC = () => (
  <ChatWorkspace
    mode="travel"
    title="AI 旅游助手"
    emptyHint="你好！我是 AI 旅游助手。请用「对话列表」「示例问题」管理会话与发起咨询（窄屏在输入区上方，宽屏在两侧）。"
    showToolPanel
    alternateHref="/chat/manus"
    alternateLabel="超级智能体"
  />
);

export default TravelChatPage;
