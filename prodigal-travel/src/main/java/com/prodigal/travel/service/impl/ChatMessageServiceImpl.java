package com.prodigal.travel.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.prodigal.travel.model.entity.ChatMessage;
import com.prodigal.travel.service.ChatMessageService;
import com.prodigal.travel.mapper.ChatMessageMapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.List;

/**
* @author 35104
* @description 针对表【chat_message(会话消息表)】的数据库操作Service实现
* @createDate 2026-04-04 16:12:02
*/
@Service
public class ChatMessageServiceImpl extends ServiceImpl<ChatMessageMapper, ChatMessage>
    implements ChatMessageService{

    @Resource
    ChatMessageMapper chatMessageMapper;

    /**
     * 查询会话消息
     * @param conversationId 会话ID
     * @param userId 用户ID
     * @param lastN 查询数量
     * @return
     */
    @Override
    public List<ChatMessage> findChatMessage(String conversationId, Long userId, int lastN){
       return chatMessageMapper.findByConversationIdAndUserId(conversationId,userId,lastN);
    }

    @Override
    public void remove(String conversationId, Long userId) {
        LambdaQueryWrapper<ChatMessage> queryWrapper = Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId);
        this.remove(queryWrapper);
    }
}




