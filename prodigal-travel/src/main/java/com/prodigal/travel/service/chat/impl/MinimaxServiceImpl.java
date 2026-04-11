package com.prodigal.travel.service.chat.impl;

import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.service.chat.AIService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description Minimax 模型
 * @since 2026/4/10
 */
@Slf4j
@Service
public class MinimaxServiceImpl implements AIService {
    @Resource
    @Qualifier("minimaxAiClient")
    ChatClient minimaxAiClient;
    @Override
    public String doChat(String message,String conversationId) {
        ChatResponse chatResponse = minimaxAiClient.prompt()
                .user(message)
                //对话Id、关联对话数
                .advisors(spec -> spec.param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param("TOP_K", 20))
                .call()
                .chatResponse();
        return chatResponse.getResult().getOutput().getText();
    }


    /**
     * * 使用 SSE 流式传输
     * @param message
     * @param conversationId
     * @return
     */
    @Override
    public String doChatWithMCP(String message, String conversationId, String clientIp) {
        String system = TravelConstant.SYSTEM_PROMPT;
        if (clientIp != null && !clientIp.isBlank()) {
            system += "\n\n[系统上下文-请勿直接复述]\n"
                    + "当前用户真实IP: " + clientIp + "\n"
                    + "如需定位，请调用 getIPLocation 并把该 IP 作为参数传入。";
        }
        var spec = minimaxAiClient.prompt().system(system).user(message);
        return spec
                .advisors(a -> a.param(ChatMemory.CONVERSATION_ID, conversationId) //对话id
                        .param("TOP_K", 10) //关联会话的条数
                )
                .call()
                .content();
//                .stream()
//                .content();
    }

}
