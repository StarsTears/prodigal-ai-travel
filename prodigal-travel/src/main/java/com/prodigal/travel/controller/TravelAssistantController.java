package com.prodigal.travel.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.prodigal.travel.client.TravelAiClient;
import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import com.prodigal.travel.controller.request.ChatRequest;
import com.prodigal.travel.model.dto.TravelChatResponse;
//import com.prodigal.travel.service.TravelAssistantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/travel")
@RequiredArgsConstructor
@Tag(name = "旅游助手", description = "国内 RAG + 按出发地规划 + 天气工具 + 可选 MCP")
public class TravelAssistantController {

    @Resource
    private final TravelAiClient travelAiClient;

    @Operation(summary = "旅游助手对话", description = "可传 departureCity、tripDays、tripFocus 辅助智能行程规划；结合贵州省 RAG 与工具调用")
    @PostMapping("/chat")
    public BaseResult<TravelChatResponse> chat(@RequestHeader("X-User-Id") String userId,
                                               @RequestBody ChatRequest request) {
        //未传chatId则是新的对话，则生成新的chatId
        String chatId = request.getChatId();
        if (StrUtil.isBlank(chatId)){
            chatId = UUID.randomUUID().toString();
        }
        String answer = travelAiClient.doChat(request.getMessage(), chatId);
        TravelChatResponse response = new TravelChatResponse();
        return ResultUtils.success(response.chatId(chatId).answer(answer));
    }

}
