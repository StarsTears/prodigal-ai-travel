package com.prodigal.travel.rag;

import jakarta.annotation.Resource;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 关键词顾问
 * @since 2026/4/1
 */
@Component
public class TravelKeywordEnricher {
    @Resource
    private ChatModel chatModel;

    /**
     * 为文档添加关键词
     * @param documents
     * @return
     */
    public List<Document> enrichDocument(List<Document> documents) {
        KeywordMetadataEnricher keywordMetadataEnricher = new KeywordMetadataEnricher(chatModel,5);
        return keywordMetadataEnricher.apply(documents);
    }
}
