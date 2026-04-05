package com.prodigal.travel.mapper;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.prodigal.travel.model.entity.ChatMessage;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.List;

/**
* @author 35104
* @description 针对表【chat_message(会话消息表)】的数据库操作Mapper
* @createDate 2026-04-04 16:12:02
* @Entity com.prodigal.travel.model.entity.ChatMessage
*/
public interface ChatMessageMapper extends BaseMapper<ChatMessage> {

    default List<ChatMessage> findByConversationIdAndUserId(String conversationId, Long userId, int lastN){
        LambdaQueryWrapper<ChatMessage> wrapper = Wrappers.<ChatMessage>lambdaQuery()
                .eq(ChatMessage::getConversationId, conversationId)
                .eq(ChatMessage::getUserId, userId)
                .orderByDesc(ChatMessage::getCreateTime)
                .last("LIMIT " + lastN);
       return this.selectList(wrapper);
    }
}




