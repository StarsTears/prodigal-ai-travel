package com.prodigal.travel.controller;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.prodigal.travel.client.TravelAiClient;
import com.prodigal.travel.common.BaseResult;
import com.prodigal.travel.common.ResultUtils;
import com.prodigal.travel.common.SseStreams;
import com.prodigal.travel.config.OpenApiSecurityConfig;
import com.prodigal.travel.constants.LoginUserConstant;
import com.prodigal.travel.controller.request.ChatRequest;
import com.prodigal.travel.controller.request.ConversationRenameRequest;
import com.prodigal.travel.controller.vo.ChatMessageVO;
import com.prodigal.travel.controller.vo.TravelChatResponse;
import com.prodigal.travel.service.ManusAgentService;
import com.prodigal.travel.service.chat.ChatConversationService;
import com.prodigal.travel.tools.ClientIpResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/travel")
@Tag(name = "travelAssistant", description = "国内 RAG + 按出发地规划 + 天气工具 + 可选 MCP")
@SecurityRequirement(name = OpenApiSecurityConfig.SECURITY_SCHEME_NAME)
public class TravelAssistantController {

    private final TravelAiClient travelAiClient;
    private final ChatConversationService chatConversationService;
    private final ManusAgentService manusAgentService;
    private final ClientIpResolver clientIpResolver;

    public TravelAssistantController(
            TravelAiClient travelAiClient,
            ChatConversationService chatConversationService,
            ManusAgentService manusAgentService,
            ClientIpResolver clientIpResolver) {
        this.travelAiClient = travelAiClient;
        this.chatConversationService = chatConversationService;
        this.manusAgentService = manusAgentService;
        this.clientIpResolver = clientIpResolver;
    }

    @Operation(summary = "旅游助手对话", description = "需登录：请求头 Authorization: Bearer {token}。同步返回完整答复。")
    @PostMapping("/chat")
    public BaseResult<TravelChatResponse> chat(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody ChatRequest request) {
        String chatId = resolveChatId(request.getChatId());
        String conversationId = loginUserId + ":" + chatId;
        String clientIp = clientIpResolver.resolveClientIp(httpServletRequest);
        String answer = travelAiClient.doChatWithMCP(
                request.getMessage(),
                conversationId,
                clientIp,
                request.getLatitude(),
                request.getLongitude(),
                request.getBrowserGeolocationStatus());
        return ResultUtils.success(new TravelChatResponse().chatId(chatId).answer(answer));
    }

    @Operation(summary = "旅游助手 RAG 流式对话", description = "查询重写 -> 向量检索 -> 提示增强 -> 流式生成。")
    @PostMapping(value = "/chat/rag")
    public SseEmitter chatRag(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            @Valid @RequestBody ChatRequest request) {
        String chatId = resolveChatId(request.getChatId());
        String conversationId = loginUserId + ":" + chatId;
        // RAG 前置有查询重写 + 向量检索，耗时较长
        return SseStreams.fromFlux(
                travelAiClient.doChatWithRag(request.getMessage(), conversationId),
                300_000L, "doChatWithRag");
    }

    @Operation(summary = "旅游助手对话（SSE 流式）", description = "Spring AI + 本地工具 + MCP 工具，逐分片输出。")
    @PostMapping(value = "/chat/sse_emitter")
    public SseEmitter chatSseEmitter(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody ChatRequest request) {
        String chatId = resolveChatId(request.getChatId());
        String conversationId = loginUserId + ":" + chatId;
        String clientIp = clientIpResolver.resolveClientIp(httpServletRequest);
        return SseStreams.fromFlux(
                travelAiClient.doChatWithMCPSSE(
                        request.getMessage(),
                        conversationId,
                        clientIp,
                        request.getLatitude(),
                        request.getLongitude(),
                        request.getBrowserGeolocationStatus()),
                180_000L, "doChatWithMCPSSE");
    }

    @Operation(
            summary = "流式调用超级智能体",
            description = "需登录。ReAct 循环结构化 SSE：event=step 为思考/工具日志，event=delta 为最终答复，event=done 为结束。"
    )
    @PostMapping(value = "/manus/chat")
    public SseEmitter chatManus(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            @Valid @RequestBody ChatRequest request) {
        return manusAgentService.stream(loginUserId, request);
    }

    @Operation(summary = "历史会话", description = "不传 chatId 时返回当前用户全部会话（仅元数据）；传 chatId 时返回该会话及全部消息。")
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

    @Operation(summary = "重命名会话", description = "修改指定会话的标题；需登录")
    @PutMapping("/conversations")
    public BaseResult<Void> renameConversation(
            @RequestAttribute(LoginUserConstant.REQUEST_ATTR_USER_ID) Long loginUserId,
            @Valid @ModelAttribute ConversationRenameRequest request) {
        chatConversationService.rename(request.getChatId(), loginUserId, request.getTitle());
        return ResultUtils.success(null);
    }

    private static String resolveChatId(String chatId) {
        return StrUtil.isBlank(chatId) ? UUID.randomUUID().toString() : chatId;
    }
}
