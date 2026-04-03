package com.prodigal.travel.rag;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 查询重写
 * @since 2026/4/1
 */
@Component
public class QueryRewriter {
    private QueryTransformer queryTransformer;

    public QueryRewriter(ChatModel dashscopeChatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(dashscopeChatModel);
        // 创建查询重写转换器
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();
    }

    /**
     * 执行查询重写
     * 原始查询转换成更加结构化和明确的形式
     * @param prompt 原始查询
     * @return
     */
    public String doRewriteQuery(String prompt) {
        Query query = new Query(prompt);
        Query transform = queryTransformer.transform(query);
        // 保留原始用户指令（例如“将内容发送到某邮箱”），并将改写内容明确标注为“仅供检索”。
        // 否则在 doWithRag() 中，用户的邮件发送意图可能在改写后丢失，导致模型不会触发 sendEmail 工具调用。
        return prompt.strip()
                + "\n\n[用于知识库检索的改写查询（不作为用户指令）]\n"
                + transform.text();
    }
}
