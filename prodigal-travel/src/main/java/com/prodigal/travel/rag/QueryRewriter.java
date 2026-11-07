package com.prodigal.travel.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.preretrieval.query.transformation.QueryTransformer;
import org.springframework.ai.rag.preretrieval.query.transformation.RewriteQueryTransformer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 查询重写
 * @since 2026/4/1
 */
@Slf4j
@Component
public class QueryRewriter {
    private QueryTransformer queryTransformer;

    public QueryRewriter(@Qualifier("dashScopeChatModel") ChatModel dashscopeChatModel) {
        ChatClient.Builder chatClientBuilder = ChatClient.builder(dashscopeChatModel);
        // 创建查询重写转换器
        queryTransformer = RewriteQueryTransformer.builder()
                .chatClientBuilder(chatClientBuilder)
                .build();
    }

    /**
     * 执行查询重写，返回可直接用于向量检索的纯改写查询。
     *
     * <p>旧版返回「原问题 + 改写查询」的拼接串，并被直接当作 user 消息送给模型：
     * 检索 query 里混入了说明文字，聊天记忆里保存的也是被改写过的问题，
     * 模型还可能把 "[用于知识库检索的改写查询]" 当成用户指令照着执行。
     * 现在检索与生成解耦：本方法只服务于检索，生成阶段始终使用原始用户消息。
     *
     * @param prompt 原始查询
     * @return 用于向量检索的改写查询；重写失败时返回原查询
     */
    public String rewriteForRetrieval(String prompt) {
        try {
            Query query = new Query(prompt);
            Query transform = queryTransformer.transform(query);
            if (transform == null || transform.text() == null || transform.text().isBlank()) {
                return prompt;
            }
            return transform.text().strip();
        } catch (Exception e) {
            log.warn("查询重写失败，使用原始查询检索：{}", e.getMessage());
            return prompt;
        }
    }

    /**
     * @deprecated 保留兼容旧调用；新链路请使用 {@link #rewriteForRetrieval(String)}
     */
    @Deprecated
    public String doRewriteQuery(String prompt) {
        return rewriteForRetrieval(prompt);
    }
}
