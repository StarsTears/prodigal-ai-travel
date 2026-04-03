/** 与后端 `com.prodigal.travel.common.BaseResult` 对齐 */
export interface BaseResult<T> {
  code: number;
  status: boolean;
  msg: string;
  data: T;
}

/** 与后端 `TravelChatResponse` 对齐（`POST /travel/chat` 的 data） */
export interface TravelChatResponse {
  chatId: string;
  answer?: string;
}

/** 与后端 `ChatRequest` 对齐的请求 JSON */
export interface TravelChatRequestBody {
  /** 用户问题，@NotBlank */
  message: string;
  /** 会话 id；不传则后端生成 */
  chatId?: string;
}
