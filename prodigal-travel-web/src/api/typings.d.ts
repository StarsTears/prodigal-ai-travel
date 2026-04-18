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
    updateTime?: string;
  };

  type ChatRequest = {
    /** 用户问题或需求描述 */
    message: string;
    /** 对话id */
    chatId?: string;
    /** 浏览器 Geolocation 纬度 */
    latitude?: number;
    /** 浏览器 Geolocation 经度 */
    longitude?: number;
    /** granted | denied | unsupported | timeout | error | pending | none */
    browserGeolocationStatus?: string;
  };

  type conversationsParams = {
    chatId?: string;
  };

  type deleteConversationParams = {
    chatId: string;
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

  type SendCodeRequest = {
    email: string;
  };

  type SseEmitter = {
    timeout?: number;
  };

  type TravelChatResponse = {
    /** 会话Id */
    chatId?: string;
    /** 模型回答 */
    answer?: string;
  };
}
