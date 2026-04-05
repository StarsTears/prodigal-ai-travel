package com.prodigal.travel.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.prodigal.travel.controller.vo.ChatMessageVO;
import com.prodigal.travel.model.entity.ChatConversation;
import com.prodigal.travel.service.ChatConversationService;
import com.prodigal.travel.mapper.ChatConversationMapper;
import com.prodigal.travel.model.entity.ChatMessage;
import com.prodigal.travel.service.ChatMessageService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;


/**
* @author 35104
* @description 针对表【chat_conversation(会话表)】的数据库操作Service实现
* @createDate 2026-04-04 15:49:24
*/
@Service
public class ChatConversationServiceImpl extends ServiceImpl<ChatConversationMapper, ChatConversation>
    implements ChatConversationService{
    @Resource
    private ChatMessageService chatMessageService;


    /**
     * 查询历史会话：未传会话 ID 时返回该用户全部会话（仅会话元数据，不含消息体）；
     * 传入会话 ID 时仅返回该会话，并附带该会话下全部消息（按时间正序）。
     */
    @Override
    public List<ChatMessageVO> findByUserId(String conversationId, Long userId) {
        LambdaQueryWrapper<ChatConversation> queryWrapper = Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getUserId, userId)
                .eq(StrUtil.isNotBlank(conversationId), ChatConversation::getConversationId, conversationId)
                .orderByDesc(ChatConversation::getUpdateTime)
                .orderByDesc(ChatConversation::getCreateTime);
        List<ChatConversation> list = this.list(queryWrapper);
        boolean withMessages = StrUtil.isNotBlank(conversationId);
        return list.stream().map(conv -> {
            List<ChatMessage> messages = Collections.emptyList();
            if (withMessages) {
                LambdaQueryWrapper<ChatMessage> msgWrapper = Wrappers.<ChatMessage>lambdaQuery()
                        .eq(ChatMessage::getConversationId, conv.getConversationId())
                        .eq(ChatMessage::getUserId, userId)
                        .orderByAsc(ChatMessage::getCreateTime);
                messages = chatMessageService.list(msgWrapper);
            }
            return ChatMessageVO.builder()
                    .conversationId(conv.getConversationId())
                    .title(conv.getTitle())
                    .messages(messages)
                    .updateTime(conv.getUpdateTime() != null ? conv.getUpdateTime() : conv.getCreateTime())
                    .build();
        }).toList();
    }

    /**
     * 删除会话
     * @param conversationId 会话id
     * @param userId 用户id
     */
    @Override
    public void remove(String conversationId, Long userId){
        chatMessageService.remove(conversationId, userId);
        LambdaQueryWrapper<ChatConversation> queryWrapper = Wrappers.<ChatConversation>lambdaQuery()
                .eq(ChatConversation::getConversationId, conversationId)
                .eq(ChatConversation::getUserId, userId);
        this.remove(queryWrapper);

    }
}




