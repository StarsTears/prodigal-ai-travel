declare namespace API {
  type BaseResultListChatMessageVO = {
    code?: number;
    status?: boolean;
    msg?: string;
    data?: ChatMessageVO[];
  };

  type BaseResultLoginResponse = {
    code?: number;
    status?: boolean;
    msg?: string;
    data?: LoginResponse;
  };

  type BaseResultString = {
    code?: number;
    status?: boolean;
    msg?: string;
    data?: string;
  };

  type BaseResultTravelChatResponse = {
    code?: number;
    status?: boolean;
    msg?: string;
    data?: TravelChatResponse;
  };

  type BaseResultVoid = {
    code?: number;
    status?: boolean;
    msg?: string;
    data?: Record<string, any>;
  };

  type ChatMessage = {
    id?: string;
    conversationId?: string;
    userId?: number;
    role?: string;
    content?: string;
    createTime?: string;
  };

  type ChatMessageVO = {
    conversationId?: string;
    title?: string;
    messages?: ChatMessage[];
    /** 会话最近活动时间（ISO 或时间戳，由 Jackson 序列化） */
    updateTime?: string | number;
  };

  type ChatRequest = {
    /** 用户问题或需求描述 */
    message: string;
    /** 对话id */
    chatId?: string;
  };

  type conversationsParams = {
    chatId?: string;
  };

  type LoginByCodeRequest = {
    email: string;
    /** 邮箱收到的 6 位数字验证码 */
    code: string;
  };

  type LoginRequest = {
    /** 账号：用户名，或完整邮箱 */
    account: string;
    password: string;
  };

  type LoginResponse = {
    /** JWT，前端可存 localStorage；需要鉴权的接口可带 Authorization: Bearer */
    token?: string;
    /** 用户主键；聊天等接口从 JWT 解析，无需再传 X-User-Id */
    userId?: number;
    username?: string;
    nickname?: string;
    email?: string;
  };

  type RegisterRequest = {
    email: string;
    username: string;
    password: string;
    /** 可选，默认同用户名 */
    nickname?: string;
  };

  type SendRegisterCodeRequest = {
    email: string;
  };

  type TravelChatResponse = {
    /** 会话Id */
    chatId?: string;
    /** 模型回答 */
    answer?: string;
  };
}
