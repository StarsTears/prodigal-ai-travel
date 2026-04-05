package com.prodigal.travel.service;

import com.prodigal.travel.model.entity.ChatMessage;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 35104
* @description 针对表【chat_message(会话消息表)】的数据库操作Service
* @createDate 2026-04-04 16:12:02
*/
public interface ChatMessageService extends IService<ChatMessage> {

    List<ChatMessage> findChatMessage(String conversationId, Long userId, int lastN);

    void remove(String conversationId, Long userId);
}
