package com.prodigal.travel.controller.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "旅游助手对话响应")
public class TravelChatResponse {

    @Schema(description = "会话Id")
    private String chatId;

    @Schema(description = "模型回答")
    private String answer;


    public TravelChatResponse chatId(String chatId){
        this.chatId = chatId;
        return this;
    }
    public TravelChatResponse answer(String answer){
        this.answer = answer;
        return this;
    }


}
