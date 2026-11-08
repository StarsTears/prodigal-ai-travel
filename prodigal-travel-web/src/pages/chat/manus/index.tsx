import React from 'react';
import { ChatWorkspace } from '@/components/chat/ChatWorkspace';

const ManusChatPage: React.FC = () => (
  <ChatWorkspace
    mode="manus"
    title="超级智能体"
    emptyHint="描述一个较复杂的目标，智能体会逐步推理、调用工具，并把执行过程与最终结论分开呈现。会话仅保存在本机。"
    showToolPanel={false}
  />
);

export default ManusChatPage;
