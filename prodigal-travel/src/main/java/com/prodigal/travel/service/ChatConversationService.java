package com.prodigal.travel.service;

import com.prodigal.travel.controller.vo.ChatMessageVO;
import com.prodigal.travel.model.entity.ChatConversation;
import com.baomidou.mybatisplus.extension.service.IService;

import java.util.List;

/**
* @author 35104
* @description 针对表【chat_conversation(会话表)】的数据库操作Service
* @createDate 2026-04-04 15:49:24
*/
public interface ChatConversationService extends IService<ChatConversation> {

    List<ChatMessageVO> findByUserId(String conversationId, Long userId);

    void remove(String conversationId, Long userId);
}
