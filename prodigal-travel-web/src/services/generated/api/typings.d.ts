declare namespace API {
  type BaseResultTravelChatResponse = {
    code?: number;
    status?: boolean;
    msg?: string;
    data?: TravelChatResponse;
  };

  type ChatRequest = {
    /** 用户问题或需求描述 */
    message: string;
    /** 对话id */
    chatId?: string;
  };

  type TravelChatResponse = {
    /** 会话Id */
    chatId?: string;
    /** 模型回答 */
    answer?: string;
  };
}
