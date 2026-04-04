package com.prodigal.travel.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.prodigal.travel.client.TravelAiClient;
import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import com.prodigal.travel.config.OpenApiSecurityConfig;
import com.prodigal.travel.controller.request.ChatRequest;
import com.prodigal.travel.controller.vo.TravelChatResponse;
import com.prodigal.travel.constants.LoginUserConstant;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/travel")
@RequiredArgsConstructor
@Tag(name = "旅游助手", description = "国内 RAG + 按出发地规划 + 天气工具 + 可选 MCP")
@SecurityRequirement(name = OpenApiSecurityConfig.SECURITY_SCHEME_NAME)
public class TravelAssistantController {

    private final TravelAiClient travelAiClient;

    @Operation(
            summary = "旅游助手对话",
            description = "需登录：请求头 Authorization: Bearer {token}。可传 departureCity、tripDays、tripFocus；结合 RAG 与工具调用。"
    )
    @PostMapping("/chat")
    public BaseResult<TravelChatResponse> chat(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            @RequestBody ChatRequest request) {
        String chatId = request.getChatId();
        if (StrUtil.isBlank(chatId)) {
            chatId = UUID.randomUUID().toString();
        }
        String memoryConversationId = loginUserId + ":" + chatId;
        String answer = travelAiClient.doChatWithMCP(request.getMessage(), memoryConversationId);
        TravelChatResponse response = new TravelChatResponse();
        return ResultUtils.success(response.chatId(chatId).answer(answer));
    }
}
