package com.prodigal.travel.service.chat;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description Ollama 服务
 * @since 2026/4/10
 */
public interface AIService {

    String doChat(String message,String conversationId);

    String doChatWithMCP(String message, String conversationId, String clientIp);
}
