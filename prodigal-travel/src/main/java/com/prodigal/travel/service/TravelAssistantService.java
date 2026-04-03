//package com.prodigal.travel.service;
//
//import com.prodigal.travel.exception.BusinessException;
//import com.prodigal.travel.exception.ResponseStatus;
//import com.prodigal.travel.model.dto.TravelChatRequest;
//import org.springframework.ai.chat.client.ChatClient;
//import org.springframework.beans.factory.annotation.Qualifier;
//import org.springframework.stereotype.Service;
//
//@Service
//public class TravelAssistantService {
//
//    private final ChatClient travelChatClient;
//
//    public TravelAssistantService(ChatClient travelChatClient) {
//        this.travelChatClient = travelChatClient;
//    }
//
//    public String chat(TravelChatRequest request) {
//        if (request.getMessage() == null || request.getMessage().isBlank()) {
//            throw new BusinessException(ResponseStatus.PARAMS_ERROR, "消息内容不能为空");
//        }
//        String userText = augmentWithStructuredHints(request);
//        return travelChatClient.prompt()
//                .user(userText)
//                .call()
//                .content();
//    }
//
//    private static String augmentWithStructuredHints(TravelChatRequest req) {
//        StringBuilder head = new StringBuilder();
//        if (req.getDepartureCity() != null && !req.getDepartureCity().isBlank()) {
//            head.append("[用户出发城市：").append(req.getDepartureCity().strip()).append("]\n");
//        }
//        if (req.getTripDays() != null && req.getTripDays() > 0) {
//            head.append("[贵州游玩天数：").append(req.getTripDays()).append("]\n");
//        }
//        if (req.getTripFocus() != null && !req.getTripFocus().isBlank()) {
//            head.append("[意向景点/区域：").append(req.getTripFocus().strip()).append("]\n");
//        }
//        if (!head.isEmpty()) {
//            head.append("\n");
//        }
//        return head + req.getMessage().strip();
//    }
//}
