package com.prodigal.travel.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 多查询扩展
 * @since 2026/4/2
 */
@Component
public class TravelMultiQueryExpander {
    private final ChatClient.Builder chatClientBuilder;
    TravelMultiQueryExpander(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel) {
        this.chatClientBuilder = ChatClient.builder(dashscopeChatModel);
    }

    public List<Query> expand(String query) {
        // 构建查询扩展器
        // 用于生成多个相关的查询变体，以获得更全面的搜索结果
        MultiQueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(chatClientBuilder)
                .includeOriginal(true) // 包含原始查询
                .numberOfQueries(3) // 生成3个查询变体
                .build();
        return queryExpander.expand(new Query(query));
    }
}
