package com.prodigal.travel.chatmemroy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigal.travel.constants.CacheKeyConstant;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.model.entity.ChatConversation;
import com.prodigal.travel.model.entity.ChatMessage;
import com.prodigal.travel.service.chat.ChatConversationService;
import com.prodigal.travel.service.chat.ChatMessageService;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 将 Spring AI 会话记忆持久化到 MySQL（chat_conversation / chat_message）。
 * <p>
 * conversationId 与 {@link com.prodigal.travel.controller.TravelAssistantController} 一致，格式为 {@code userId:前端chatId}，
 * 入库时拆成 user_id + 业务会话 UUID（conversation_id）。
 */
@Slf4j
@Component
public class MySQLChatMemory implements ChatMemory {

    private static final long CACHE_TTL_MINUTES = 60;

    @Resource
    private ChatConversationService chatConversationService;
    @Resource
    private ChatMessageService chatMessageService;
    @Autowired
    @Qualifier("stringRedisTemplate")
    private StringRedisTemplate redisTemplate;
    @Resource
    private ObjectMapper objectMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void add(String conversationId, List<Message> messages) {
        if (!StringUtils.hasText(conversationId) || CollectionUtils.isEmpty(messages)) {
            return;
        }
        ParsedConversation parsed = ParsedConversation.parse(conversationId);
        ensureConversation(parsed,messages.getFirst().getText());

        List<ChatMessage> rows = new ArrayList<>(messages.size());
        for (Message message : messages) {
            if (message == null) {
                continue;
            }
            String text = message.getText();
            if (!StringUtils.hasText(text)) {
                continue;
            }
            MessageType type = message.getMessageType();
            String role = type != null ? type.getValue() : MessageType.USER.getValue();

            ChatMessage row = ChatMessage.builder()
                    .conversationId(parsed.chatUuid())
                    .userId(parsed.userId())
                    .role(role)
                    .content(text)
                    .build();
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            chatMessageService.saveBatch(rows);
            chatConversationService.lambdaUpdate()
                    .eq(ChatConversation::getConversationId, parsed.chatUuid())
                    .eq(ChatConversation::getUserId, parsed.userId())
                    .set(ChatConversation::getUpdateTime, new Date())
                    .update();
            cacheRecentMessages(parsed, rows);
        }
    }

    @Override
    public List<Message> get(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        ParsedConversation parsed = ParsedConversation.parse(conversationId);
        List<Message> cachedMessages = getCachedMessages(parsed);
        if (!cachedMessages.isEmpty()) {
            return cachedMessages;
        }

        List<ChatMessage> rows = chatMessageService.findChatMessage(parsed.chatUuid(), parsed.userId(), TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT);
        Collections.reverse(rows);
        List<Message> out = new ArrayList<>(rows.size());
        for (ChatMessage row : rows) {
            out.add(toSpringMessage(row.getRole(), row.getContent()));
        }
        cacheRecentMessages(parsed, rows);
        return out;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        ParsedConversation parsed = ParsedConversation.parse(conversationId);
        chatConversationService.remove(parsed.chatUuid(), parsed.userId());
        redisTemplate.delete(buildCacheKey(parsed));
    }

    private void ensureConversation(ParsedConversation parsed,String title) {
        long count = chatConversationService.lambdaQuery()
                .eq(ChatConversation::getConversationId, parsed.chatUuid())
                .eq(ChatConversation::getUserId, parsed.userId())
                .eq(ChatConversation::getDeleted, 0)
                .count();
        if (count > 0) {
            return;
        }
        ChatConversation conversation = ChatConversation.builder()
                .conversationId(parsed.chatUuid())
                .userId(parsed.userId())
                .title(title.substring(0, Math.min(title.length(), 200)))
                .build();
        chatConversationService.save(conversation);
    }

    private static Message toSpringMessage(String role, String content) {
        String text = content != null ? content : "";
        if (!StringUtils.hasText(role)) {
            return new UserMessage(text);
        }
        String r = role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "assistant" -> new AssistantMessage(text);
            case "system" -> new SystemMessage(text);
            case "tool", "function" -> new AssistantMessage(text);
            default -> new UserMessage(text);
        };
    }

    private void cacheRecentMessages(ParsedConversation parsed, List<ChatMessage> rows) {
        if (rows == null || rows.isEmpty()) {
            return;
        }
        String cacheKey = buildCacheKey(parsed);
        try {
            for (ChatMessage row : rows) {
                if (row == null || !StringUtils.hasText(row.getContent())) {
                    continue;
                }
                CachedMessage cacheRow = new CachedMessage(row.getRole(), row.getContent());
                redisTemplate.opsForList().rightPush(cacheKey, objectMapper.writeValueAsString(cacheRow));
            }
            redisTemplate.opsForList().trim(cacheKey, -TravelConstant.CHAT_MEMORY_MESSAGE_LIMIT, -1);
            redisTemplate.expire(cacheKey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 缓存异常不应影响主链路（仍可依赖 MySQL 记忆）
            log.error("Failed to cache recent messages for conversation: {}", parsed.chatUuid(), e);
        }
    }

    private List<Message> getCachedMessages(ParsedConversation parsed) {
        String cacheKey = buildCacheKey(parsed);
        try {
            List<String> cacheRows = redisTemplate.opsForList().range(cacheKey, 0, -1);
            if (cacheRows == null || cacheRows.isEmpty()) {
                return List.of();
            }
            List<Message> messages = new ArrayList<>(cacheRows.size());
            for (String row : cacheRows) {
                if (!StringUtils.hasText(row)) {
                    continue;
                }
                CachedMessage cachedMessage = objectMapper.readValue(row, CachedMessage.class);
                messages.add(toSpringMessage(cachedMessage.role(), cachedMessage.content()));
            }
            redisTemplate.expire(cacheKey, CACHE_TTL_MINUTES, TimeUnit.MINUTES);
            return messages;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * prodigal:chat:memory:recent:userid:chatid
     * @param parsed
     * @return
     */
    private String buildCacheKey(ParsedConversation parsed) {
        return String.format("%s%s:%s", CacheKeyConstant.CHAT_KEY_PREFIX, parsed.userId(), parsed.chatUuid());
    }

    private record CachedMessage(String role, String content) {
    }

    private record ParsedConversation(long userId, String chatUuid) {

        static ParsedConversation parse(String conversationId) {
            if (!StringUtils.hasText(conversationId)) {
                return new ParsedConversation(0L, "default");
            }
            int i = conversationId.indexOf(':');
            if (i <= 0 || i == conversationId.length() - 1) {
                return new ParsedConversation(0L, conversationId.trim());
            }
            try {
                long uid = Long.parseLong(conversationId.substring(0, i).trim());
                String uuid = conversationId.substring(i + 1).trim();
                return new ParsedConversation(uid, uuid);
            } catch (NumberFormatException e) {
                return new ParsedConversation(0L, conversationId.trim());
            }
        }
    }
}
