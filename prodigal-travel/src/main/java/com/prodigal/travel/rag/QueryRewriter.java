package com.prodigal.travel.rag;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * 检索查询的本地规整器。
 *
 * <p>旧版使用 {@code RewriteQueryTransformer} 调用大模型改写查询：每次 RAG 请求都会额外
 * 产生一次外部模型调用，且改写结果不可控（可能把「拉萨」扩写成其它目的地），与
 * 「RAG 只从知识库取信息」的目标相冲突。现在改为纯字符串规整：剥离口语前缀、
 * 折叠空白、限制长度，保留用户原文里的实体与意图词，全程不访问网络。
 */
@Slf4j
@Component
public class QueryRewriter {

    private static final int MAX_QUERY_LENGTH = 120;

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");

    private static final Pattern ORAL_PREFIX = Pattern.compile(
            "^(请问|请帮我|帮忙|帮我|麻烦(你|问一下)?|我想(知道|了解|问一下|咨询)|我想|"
                    + "给我(介绍|说说|讲讲|推荐)|给我|能不能(介绍|说说|讲讲)|"
                    + "可以(介绍|说说|讲讲)吗?|想问(一下)?|问一下|谁知道|"
                    + "来点|说说|介绍一下|介绍下|关于)[，,、:：\\s?？]*");

    private static final Pattern LEADING_PUNCT = Pattern.compile("^[，,、:：\\s?？!！.。]+");

    /**
     * 返回可直接用于向量检索的查询串；任何异常都回退到原问题，不会调用外部接口。
     */
    public String rewriteForRetrieval(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            return prompt == null ? "" : prompt.strip();
        }
        String original = prompt.strip();
        String q = WHITESPACE.matcher(original).replaceAll(" ").strip();
        String previous;
        do {
            previous = q;
            q = ORAL_PREFIX.matcher(q).replaceFirst("").strip();
            q = LEADING_PUNCT.matcher(q).replaceFirst("").strip();
        } while (!q.equals(previous) && !q.isBlank());
        if (q.isBlank()) {
            return original;
        }
        if (q.length() > MAX_QUERY_LENGTH) {
            q = q.substring(0, MAX_QUERY_LENGTH);
        }
        return q;
    }

    /**
     * @deprecated 保留兼容旧调用；新链路请使用 {@link #rewriteForRetrieval(String)}
     */
    @Deprecated
    public String doRewriteQuery(String prompt) {
        return rewriteForRetrieval(prompt);
    }
}
