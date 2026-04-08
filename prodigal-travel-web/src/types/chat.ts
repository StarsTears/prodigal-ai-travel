export type ChatRole = 'user' | 'assistant' | 'system';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  createdAt: number;
  streaming?: boolean;
  /** 超级智能体：后端 step 事件，用于折叠「思考与执行过程」分步卡片 */
  manusSteps?: string[];
}

export interface ConversationRecord {
  id: string;
  /** 后端会话 id，用于多轮对话 */
  backendChatId?: string;
  title: string;
  messages: ChatMessage[];
  updatedAt: number;
}
