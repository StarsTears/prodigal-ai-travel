//package com.prodigal.travel.rag;
//
//import jakarta.annotation.Resource;
//import org.springframework.ai.document.Document;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.vectorstore.SimpleVectorStore;
//import org.springframework.ai.vectorstore.VectorStore;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.util.List;
//
///**
// * @author Lang
// * @project prodigal-ai-travel
// * @Version: 1.0
// * @description 初始化向量数据库并保存文档-基于内存
// * @since 2026/4/1
// */
//@Configuration("travelVectorStoreSpringConfiguration")
//public class TravelVectorStoreConfig {
//    @Resource
//    private TravelKnowledgeLoader travelKnowledgeLoader;
//    @Resource
//    private TravelTokenTextSplitter travelTokenTextSplitter;
//    @Resource
//    private TravelKeywordEnricher travelKeywordEnricher;
//
//    @Bean
//    VectorStore travelVectorStore(EmbeddingModel embeddingModel) {
//        SimpleVectorStore simpleVectorStore = SimpleVectorStore
//                .builder(embeddingModel)//大语言模型
//                .build();
//        //加载文档
//        List<Document> documents = travelKnowledgeLoader.loadMarkdown();
//        //切分文档
//        List<Document> splitDocuments = travelTokenTextSplitter.splitCustomized(documents);
//        //添加关键词
//        List<Document> enrichDocument = travelKeywordEnricher.enrichDocument(splitDocuments);
//
//        simpleVectorStore.add(enrichDocument);
//        return simpleVectorStore;
//    }
//}
