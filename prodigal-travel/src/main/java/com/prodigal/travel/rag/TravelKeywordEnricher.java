package com.prodigal.travel.rag;

import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 为知识库分块补充关键词元数据。
 *
 * <p>旧版用 {@code KeywordMetadataEnricher} 调大模型生成关键词：建库阶段会额外产生外部模型调用，
 * 且模型可能写入知识库原文里不存在的词。现在改为从标题与正文做纯本地提取，
 * 保证写进元数据的每个词都来自文档本身。
 */
@Component
public class TravelKeywordEnricher {

    private static final Pattern TITLE = Pattern.compile("(?m)^#{1,3}\\s*(.+)");
    private static final Pattern CJK_WORD = Pattern.compile("[\\u4e00-\\u9fa5]{2,8}");
    private static final int MAX_KEYWORDS = 5;

    public List<Document> enrichDocument(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return documents;
        }
        List<Document> result = new ArrayList<>(documents.size());
        for (Document document : documents) {
            Map<String, Object> metadata = new LinkedHashMap<>(
                    document.getMetadata() == null ? Map.of() : document.getMetadata());
            metadata.put("keywords", extractKeywords(document.getText()));
            result.add(new Document(document.getId(), document.getText(), metadata));
        }
        return result;
    }

    private static List<String> extractKeywords(String text) {
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        if (text == null || text.isBlank()) {
            return List.of();
        }
        Matcher title = TITLE.matcher(text);
        if (title.find()) {
            String topic = title.group(1)
                    .replaceAll("(旅游指南|旅游攻略|攻略|指南|介绍)$", "")
                    .trim();
            if (topic.length() >= 2) {
                keywords.add(topic);
            }
        }
        Matcher words = CJK_WORD.matcher(text);
        while (words.find() && keywords.size() < MAX_KEYWORDS) {
            String word = words.group();
            keywords.add(word.length() > 6 ? word.substring(0, 6) : word);
        }
        return new ArrayList<>(keywords);
    }
}
