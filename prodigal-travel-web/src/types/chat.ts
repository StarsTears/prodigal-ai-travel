export type ChatRole = 'user' | 'assistant' | 'system';

export interface ChatMessage {
  id: string;
  role: ChatRole;
  content: string;
  createdAt: number;
  streaming?: boolean;
}

export interface ConversationRecord {
  id: string;
  /** 后端会话 id，用于多轮对话 */
  backendChatId?: string;
  title: string;
  messages: ChatMessage[];
  updatedAt: number;
}
