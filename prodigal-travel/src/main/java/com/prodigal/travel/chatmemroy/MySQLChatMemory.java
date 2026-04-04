//package com.prodigal.travel.chatmemroy;
//
//import jakarta.annotation.Resource;
//import org.springframework.ai.chat.memory.ChatMemory;
//import org.springframework.ai.chat.messages.Message;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
///**
// * @author Lang
// * @project prodigal-ai-travel
// * @Version: 1.0
// * @description pgSQL 记录会话记忆
// * @since 2026/4/1
// */
//@Component
//public class MySQLChatMemory implements ChatMemory {
//    @Resource
//    private ChatMessageService chatMessageService;
//
//    @Override
//    public void add(String conversationId, List<Message> messages) {
//
//    }
//
//    @Override
//    public List<Message> get(String conversationId, int lastN) {
//        return List.of();
//    }
//
//    @Override
//    public void clear(String conversationId) {
//
//    }
//}
