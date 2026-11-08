package com.prodigal.travel.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prodigal.travel.chatmemroy.MySQLChatMemory;
import com.prodigal.travel.rag.QueryRewriter;
import com.prodigal.travel.service.ManusAgentService;
import com.prodigal.travel.service.alert.AiModelAlertService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

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
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        // 直接注册成已构造好的单例，Mock 对象不再走一遍 BeanPostProcessor 的字段注入
        var singletons = ctx.getBeanFactory();
        singletons.registerSingleton("dashScopeChatModel", mock(ChatModel.class));
        singletons.registerSingleton("mySQLChatMemory", mock(MySQLChatMemory.class));
        singletons.registerSingleton("queryRewriter", mock(QueryRewriter.class));
        singletons.registerSingleton("aiModelAlertService", mock(AiModelAlertService.class));
        singletons.registerSingleton("allTools", new ToolCallback[0]);
        singletons.registerSingleton("manusAgentExecutor", mock(ExecutorService.class));
        singletons.registerSingleton("objectMapper", new ObjectMapper());
        singletons.registerSingleton("pgVectorStore", mock(VectorStore.class));
        if (withSecondStore) {
            singletons.registerSingleton("anotherVectorStore", mock(VectorStore.class));
        }
        ctx.registerBean(TravelAiClient.class);
        return ctx;
    }
}
