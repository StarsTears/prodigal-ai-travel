package com.prodigal.travel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigal.travel.chatmemroy.MySQLChatMemory;
import com.prodigal.travel.constants.TravelConstant;
import com.prodigal.travel.rag.QueryRewriter;
import com.prodigal.travel.rag.RagKnowledgeRetriever;
import com.prodigal.travel.service.ManusAgentService;
import com.prodigal.travel.service.alert.AiModelAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * 启动期依赖注入回归测试（不连接 MySQL / Redis / 大模型 / pgvector）。
 *
 * <p>旧实现用 {@code @Resource private ObjectProvider<VectorStore> pgVectorStore;}，
 * 字段名与 PgVectorStoreConfig 的 {@code @Bean pgVectorStore} 同名。JSR-250 按名注入会让 Spring
 * 把 PgVectorStore 适配成 ObjectProvider，启动期抛 BeanNotOfRequiredTypeException。
 * 这里用最小 Spring 容器复现该 Bean 组合，确保改为按类型注入后上下文可以刷新。
 */
class TravelAiClientInjectionTest {

    @Test
    void objectProviderCanCoexistWithSameNamedVectorStoreBean() {
        AnnotationConfigApplicationContext ctx = baseContext(false);
        try {
            ctx.refresh();
            TravelAiClient client = ctx.getBean(TravelAiClient.class);
            assertNotNull(client, "TravelAiClient 应能被创建");
            assertSame(ctx.getBean("pgVectorStore"), client.resolveVectorStore(),
                    "ObjectProvider 应解析到名为 pgVectorStore 的向量库 Bean");
        } finally {
            ctx.close();
        }
    }

    @Test
    void multipleVectorStoreBeansDoNotBreakRag() {
        AnnotationConfigApplicationContext ctx = baseContext(true);
        try {
            ctx.refresh();
            TravelAiClient client = ctx.getBean(TravelAiClient.class);
            assertNotNull(client.resolveVectorStore(), "存在多个向量库实现时仍应返回一个可用实例");
        } finally {
            ctx.close();
        }
    }

    @Test
    void ragShortCircuitsWhenKnowledgeBaseHasNoEvidence() {
        AnnotationConfigApplicationContext ctx = baseContext(false);
        try {
            ctx.refresh();
            TravelAiClient client = ctx.getBean(TravelAiClient.class);
            List<String> chunks = client.doChatWithRag("拉萨有什么好玩的", "1:demo")
                    .collectList().block(Duration.ofSeconds(10));
            assertEquals(TravelConstant.RAG_NO_RESULT_ANSWER,
                    String.join("", chunks == null ? List.of() : chunks),
                    "知识库无证据时应直接返回固定拒答，不得调用生成模型");
        } finally {
            ctx.close();
        }
    }

    @Test
    void ragAnswerIsPersistedToChatMemoryAfterStreamCompletes() {
        AnnotationConfigApplicationContext ctx = baseContext(false);
        try {
            ctx.refresh();
            TravelAiClient client = ctx.getBean(TravelAiClient.class);
            MySQLChatMemory chatMemory = ctx.getBean("mySQLChatMemory", MySQLChatMemory.class);
            String question = "拉萨有什么好玩的";
            String conversationId = "1:demo";

            List<String> chunks = client.doChatWithRag(question, conversationId)
                    .collectList().block(Duration.ofSeconds(10));
            assertNotNull(chunks);

            // 落库在 boundedElastic 异步执行，用 Mockito timeout 等待；
            // 断言写入的是「原问题 + 完整答复」，且会话键与 Controller 一致
            verify(chatMemory, timeout(3000)).add(
                    eq(conversationId),
                    argThat((List<Message> msgs) -> msgs != null
                            && msgs.size() == 2
                            && question.equals(msgs.get(0).getText())
                            && TravelConstant.RAG_NO_RESULT_ANSWER.equals(msgs.get(1).getText())));
        } finally {
            ctx.close();
        }
    }

    @Test
    void ragFailureDoesNotPersistPartialAnswer() {
        // 没有 VectorStore Bean -> doChatWithRag 直接 Flux.error，不应写入任何消息
        AnnotationConfigApplicationContext ctx = baseContext(false, false);
        try {
            ctx.refresh();
            TravelAiClient client = ctx.getBean(TravelAiClient.class);
            MySQLChatMemory chatMemory = ctx.getBean("mySQLChatMemory", MySQLChatMemory.class);

            assertThrows(RuntimeException.class,
                    () -> client.doChatWithRag("黄山最佳季节", "1:demo")
                            .collectList().block(Duration.ofSeconds(10)));
            verify(chatMemory, never()).add(anyString(), anyList());
        } finally {
            ctx.close();
        }
    }

    @Test
    void manusAgentServiceDependenciesResolve() {
        AnnotationConfigApplicationContext ctx = baseContext(false);
        ctx.registerBean(ManusAgentService.class);
        try {
            ctx.refresh();
            assertNotNull(ctx.getBean(ManusAgentService.class),
                    "ToolCallback[] 数组按类型注入应能命中名为 allTools 的 Bean");
        } finally {
            ctx.close();
        }
    }

    private static AnnotationConfigApplicationContext baseContext(boolean withSecondStore) {
        return baseContext(withSecondStore, true);
    }

    private static AnnotationConfigApplicationContext baseContext(boolean withSecondStore,
                                                                   boolean withVectorStore) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        // 直接注册成已构造好的单例，Mock 对象不再走一遍 BeanPostProcessor 的字段注入
        var singletons = ctx.getBeanFactory();
        singletons.registerSingleton("dashScopeChatModel", mock(ChatModel.class));
        singletons.registerSingleton("mySQLChatMemory", mock(MySQLChatMemory.class));
        singletons.registerSingleton("queryRewriter", mock(QueryRewriter.class));
        singletons.registerSingleton("ragKnowledgeRetriever", mock(RagKnowledgeRetriever.class));
        singletons.registerSingleton("aiModelAlertService", mock(AiModelAlertService.class));
        singletons.registerSingleton("allTools", new ToolCallback[0]);
        singletons.registerSingleton("manusAgentExecutor", mock(ExecutorService.class));
        singletons.registerSingleton("objectMapper", new ObjectMapper());
        if (withVectorStore) {
            singletons.registerSingleton("pgVectorStore", mock(VectorStore.class));
            if (withSecondStore) {
                singletons.registerSingleton("anotherVectorStore", mock(VectorStore.class));
            }
        }
        ctx.registerBean(TravelAiClient.class);
        return ctx;
    }
}
