package com.prodigal.travel.service;

import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigal.travel.agent.AgentRunContext;
import com.prodigal.travel.agent.AgentStepEvent;
import com.prodigal.travel.agent.ProdigalManus;
import com.prodigal.travel.chatmemroy.MySQLChatMemory;
import com.prodigal.travel.controller.request.ChatRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 超级智能体门面：负责会话隔离、会话记忆、线程池调度与结构化 SSE 输出。
 *
 * <p>Controller 不再自己 {@code new ProdigalManus(...)}；每次请求在这里创建独立的
 * {@link AgentRunContext}，并用 {@code userId:chatId} 作为记忆键，Manus 也支持多轮对话。
 */
@Slf4j
@Service
public class ManusAgentService {

    private static final long SSE_TIMEOUT_MILLIS = 300_000L;

    private final ChatModel chatModel;
    private final ToolCallback[] allTools;
    private final ObjectProvider<ToolCallbackProvider> toolCallbackProviders;
    private final MySQLChatMemory chatMemory;
    private final ExecutorService agentExecutor;
    private final ObjectMapper objectMapper;

    public ManusAgentService(
            @Qualifier("dashScopeChatModel") ChatModel chatModel,
            ToolCallback[] allTools,
            ObjectProvider<ToolCallbackProvider> toolCallbackProviders,
            MySQLChatMemory chatMemory,
            @Qualifier("manusAgentExecutor") ExecutorService agentExecutor,
            ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.allTools = allTools;
        this.toolCallbackProviders = toolCallbackProviders;
        this.chatMemory = chatMemory;
        this.agentExecutor = agentExecutor;
        this.objectMapper = objectMapper;
    }

    /**
     * @return SSE emitter；事件约定见 docs/OPTIMIZATION.md
     */
    public SseEmitter stream(Long userId, ChatRequest request) {
        String message = request.getMessage();
        if (StrUtil.isBlank(message)) {
            throw new IllegalArgumentException("消息内容不能为空");
        }
        String chatId = StrUtil.isBlank(request.getChatId())
                ? UUID.randomUUID().toString() : request.getChatId();
        String conversationId = userId + ":" + chatId;

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        AtomicReference<AgentRunContext> contextRef = new AtomicReference<>();

        emitter.onTimeout(() -> {
            cancel(contextRef);
            log.warn("Manus SSE 超时, conversationId={}", conversationId);
        });
        emitter.onCompletion(() -> cancel(contextRef));
        emitter.onError(error -> {
            cancel(contextRef);
            log.warn("Manus SSE 连接异常, conversationId={}, error={}", conversationId, error.getMessage());
        });

        agentExecutor.execute(() -> {
            try {
                List<Message> history = loadHistory(conversationId);
                ProdigalManus manus = new ProdigalManus(chatModel, allTools,
                        toolCallbackProviders.getIfAvailable());
                AgentRunContext ctx = manus.createContext(history, message);
                contextRef.set(ctx);
                manus.run(ctx, event -> emit(emitter, event, contextRef));

                if (!ctx.isCancelled()) {
                    saveHistory(conversationId, message, ctx.getFinalAnswer());
                    emitter.send(SseEmitter.event().name("done").data("[DONE]"));
                }
                emitter.complete();
            } catch (Exception e) {
                log.error("Manus 执行失败, conversationId={}", conversationId, e);
                try {
                    sendStep(emitter, "执行错误：" + e.getMessage());
                    emitter.complete();
                } catch (IOException ioException) {
                    emitter.completeWithError(ioException);
                }
            }
        });
        return emitter;
    }

    private void emit(SseEmitter emitter, AgentStepEvent event,
                      AtomicReference<AgentRunContext> contextRef) {
        if (event.type() == AgentStepEvent.Type.DONE) {
            return;
        }
        String eventName = event.type() == AgentStepEvent.Type.FINAL ? "delta" : "step";
        String field = event.type() == AgentStepEvent.Type.FINAL ? "delta" : "data";
        String payload = toJson(Map.of(field, event.text()));
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException e) {
            // 客户端已断开：尽快让 Agent Loop 退出，不再浪费模型调用
            log.info("Manus SSE 写入失败，停止执行：{}", e.getMessage());
            cancel(contextRef);
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "\"\"";
        }
    }

    private void sendStep(SseEmitter emitter, String text) throws IOException {
        emitter.send(SseEmitter.event().name("step").data(toJson(Map.of("data", text))));
    }

    private void cancel(AtomicReference<AgentRunContext> contextRef) {
        AgentRunContext ctx = contextRef.get();
        if (ctx != null) {
            ctx.setCancelled(true);
        }
    }

    private List<Message> loadHistory(String conversationId) {
        try {
            return chatMemory.get(conversationId);
        } catch (Exception e) {
            // 记忆不可用不应阻断本次问答
            log.warn("读取 Manus 会话记忆失败 conversationId={}: {}", conversationId, e.getMessage());
            return List.of();
        }
    }

    private void saveHistory(String conversationId, String message, String answer) {
        if (StrUtil.isBlank(answer)) {
            return;
        }
        try {
            chatMemory.add(conversationId, List.of(new UserMessage(message), new AssistantMessage(answer)));
        } catch (Exception e) {
            log.warn("写入 Manus 会话记忆失败 conversationId={}: {}", conversationId, e.getMessage());
        }
    }
}
