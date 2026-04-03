package com.prodigal.travel.rag;

import com.prodigal.travel.constants.TravelConstant;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.RetrievalAugmentationAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.generation.augmentation.ContextualQueryAugmenter;
import org.springframework.ai.rag.preretrieval.query.expansion.MultiQueryExpander;
import org.springframework.ai.rag.preretrieval.query.expansion.QueryExpander;
import org.springframework.ai.rag.retrieval.join.ConcatenationDocumentJoiner;
import org.springframework.ai.rag.retrieval.join.DocumentJoiner;
import org.springframework.ai.rag.retrieval.search.VectorStoreDocumentRetriever;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description rag 顾问工厂
 * @since 2026/4/2
 */
public class TravelRagCustomAdvisorFactory {

    /**
     * 创建检索增强顾问
     * 包含 查询扩展、文档检索、文档合并、上下文感知查询增强
     * @param vectorStore 向量数据库
     * @param dashscopeChatModel 聊天模型
     * @return
     */
    public static Advisor createRetrievalAugmentationAdvisor(VectorStore vectorStore, ChatModel dashscopeChatModel) {
        //创建查询扩展器
        QueryExpander queryExpander = MultiQueryExpander.builder()
                .chatClientBuilder(ChatClient.builder(dashscopeChatModel))
                .includeOriginal( true)//包含原始查询
                .numberOfQueries(3) //生成3个查询变体
                .build();
        //创建文档检索器
        VectorStoreDocumentRetriever vectorStoreDocumentRetriever = VectorStoreDocumentRetriever.builder()
                .vectorStore(vectorStore)
                .similarityThreshold(0.7)    // 相似度阈值
                .topK(3)                     // 返回文档数量
                .build();

        //创建文档合并器
        DocumentJoiner documentJoiner = new ConcatenationDocumentJoiner();
        //创建上下文感知查询增强器
        ContextualQueryAugmenter queryAugmenter = ContextualQueryAugmenter.builder()
                .allowEmptyContext(true)  // 允许空上下文，避免NPE
                .emptyContextPromptTemplate(new PromptTemplate(TravelConstant.ERROR_PROMPT))
                .build();
        return RetrievalAugmentationAdvisor.builder()
                .queryExpander(queryExpander)
                //文档检索器
                .documentRetriever(vectorStoreDocumentRetriever)
                //文档查询器-错误处理和边界情况 上下文感知
                .queryAugmenter(queryAugmenter)
                .build();
    }
}
