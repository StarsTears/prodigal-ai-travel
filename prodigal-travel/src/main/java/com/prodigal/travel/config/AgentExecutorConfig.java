package com.prodigal.travel.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 专用线程池。
 *
 * <p>旧版 {@code CompletableFuture.runAsync} 使用 ForkJoinPool.commonPool：
 * 一次 Agent 运行包含多次阻塞式模型/工具调用，并发请求会把公共线程池占满，
 * 进而拖慢整个 JVM 里的并行流、异步任务甚至其它接口。这里改为有界、隔离、可观测的线程池。
 */
@Configuration
public class AgentExecutorConfig {

    @Bean(name = "manusAgentExecutor", destroyMethod = "shutdown")
    public ExecutorService manusAgentExecutor(
            @Value("${prodigal.agent.pool-size:6}") int poolSize,
            @Value("${prodigal.agent.queue-capacity:48}") int queueCapacity) {
        AtomicInteger seq = new AtomicInteger();
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "manus-agent-" + seq.incrementAndGet());
            thread.setDaemon(false);
            return thread;
        };
        return new ThreadPoolExecutor(
                poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)),
                factory,
                new ThreadPoolExecutor.CallerRunsPolicy());
    }
}
