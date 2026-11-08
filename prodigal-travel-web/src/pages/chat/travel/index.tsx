import React from 'react';
import { ChatWorkspace } from '@/components/chat/ChatWorkspace';

const TravelChatPage: React.FC = () => (
  <ChatWorkspace
    mode="travel"
    title="AI 旅游助手"
    emptyHint="问路线、查天气、找景点，或切换到「知识库 RAG」只依据已入库资料作答。"
    showToolPanel
  />
);

export default TravelChatPage;
