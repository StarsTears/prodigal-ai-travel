package com.prodigal.travel.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.prodigal.travel.client.TravelAiClient;
import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import com.prodigal.travel.config.OpenApiSecurityConfig;
import com.prodigal.travel.controller.request.ChatRequest;
import com.prodigal.travel.controller.vo.ChatMessageVO;
import com.prodigal.travel.controller.vo.TravelChatResponse;
import com.prodigal.travel.constants.LoginUserConstant;
import com.prodigal.travel.service.ChatConversationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/travel")
@RequiredArgsConstructor
@Tag(name = "travelAssistant", description = "国内 RAG + 按出发地规划 + 天气工具 + 可选 MCP")
@SecurityRequirement(name = OpenApiSecurityConfig.SECURITY_SCHEME_NAME)
public class TravelAssistantController {

    private final TravelAiClient travelAiClient;
    private final ChatConversationService chatConversationService;

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

    @Operation(
            summary = "历史会话",
            description = "不传 chatId 时返回当前用户全部会话（仅元数据）；传 chatId 时返回该会话及全部消息。"
    )
    @GetMapping("/conversations")
    public BaseResult<List<ChatMessageVO>> conversations(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            @RequestParam(required = false) String chatId) {
        return ResultUtils.success(chatConversationService.findByUserId(chatId, loginUserId));
    }

    @Operation(summary = "删除会话", description = "删除指定会话及其消息；需登录")
    @DeleteMapping("/conversations")
    public BaseResult<Void> deleteConversation(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            @RequestParam String chatId) {
        chatConversationService.remove(chatId, loginUserId);
        return ResultUtils.success(null);
    }

}
