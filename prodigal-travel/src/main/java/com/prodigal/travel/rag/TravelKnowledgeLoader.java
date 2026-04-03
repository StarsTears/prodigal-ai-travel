package com.prodigal.travel.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.markdown.MarkdownDocumentReader;
import org.springframework.ai.reader.markdown.config.MarkdownDocumentReaderConfig;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;


/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 知识库加载器 按照语义对md 文件进行切割
 * @since 2026/4/1
 */
@Slf4j
@Component
public class TravelKnowledgeLoader {
    private final ResourcePatternResolver resourcePatternResolver;
    private final TravelTokenTextSplitter travelTokenTextSplitter;
    private final TravelKeywordEnricher travelKeywordEnricher;
    private static final Pattern SEMANTIC_SPLIT_PATTERN = Pattern.compile(
            "(?m)(?=^#{1,3}\\s)|\\n\\s*\\n|(?m)(?=^---+$)"
    );

    TravelKnowledgeLoader(ResourcePatternResolver resourcePatternResolver,
                          TravelTokenTextSplitter travelTokenTextSplitter,
                          TravelKeywordEnricher travelKeywordEnricher) {
        this.resourcePatternResolver = resourcePatternResolver;
        this.travelTokenTextSplitter = travelTokenTextSplitter;
        this.travelKeywordEnricher = travelKeywordEnricher;
    }

    List<Document> loadMarkdown(){
        List<Document> documentArrayList = new ArrayList<>();
        try {
            Resource[] resources = resourcePatternResolver.getResources("classpath:/rag/*.md");
            for (Resource resource : resources) {
                MarkdownDocumentReaderConfig config = MarkdownDocumentReaderConfig.builder()
                        .withHorizontalRuleCreateDocument(true)
                        .withIncludeCodeBlock(false)
                        .withIncludeBlockquote(false)
                        .withAdditionalMetadata("filename", resource.getFilename())
                        .build();

                MarkdownDocumentReader reader = new MarkdownDocumentReader(resource, config);
                List<Document> sourceDocuments = reader.read();
                documentArrayList.addAll(splitBySemantic(sourceDocuments, resource.getFilename()));
            }
            return documentArrayList;
        } catch (IOException e) {
            log.error("markdown 加载失败",e);
            throw new RuntimeException(e);
        }
    }

    private List<Document> splitBySemantic(List<Document> sourceDocuments, String filename) {
        List<Document> semanticDocuments = new ArrayList<>();
        int globalChunkIndex = 0;
        for (Document sourceDocument : sourceDocuments) {
            String content = sourceDocument.getText();
            if (content == null || content.isBlank()) {
                continue;
            }
            String[] semanticChunks = SEMANTIC_SPLIT_PATTERN.split(content);
            for (String semanticChunk : semanticChunks) {
                String normalized = semanticChunk == null ? "" : semanticChunk.trim();
                if (normalized.isBlank()) {
                    continue;
                }
                Map<String, Object> metadata = new LinkedHashMap<>(sourceDocument.getMetadata());
                metadata.put("filename", filename);
                metadata.put("segmentType", "semantic");
                metadata.put("semanticChunkIndex", globalChunkIndex++);
                semanticDocuments.add(new Document(normalized, metadata));
            }
        }
        return semanticDocuments;
    }
}
