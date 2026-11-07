package com.prodigal.travel.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 把 Spring AI 的 {@link Flux} 文本流安全地桥接到 {@link SseEmitter}。
 *
 * <p>旧版 Controller 里三处重复 {@code flux.subscribe(...)}，且直接
 * {@code sseEmitter.send(chunk)}：分片里只要含换行就会破坏 SSE 帧（一个事件被拆成多行 data，
 * 前端只取第一行或直接解析失败）；此外超时/客户端断开后没有取消订阅，模型流仍在后台跑。
 */
@Slf4j
public final class SseStreams {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SseStreams() {}

    /**
     * @param flux          模型文本流
     * @param timeoutMillis SSE 超时
     * @param operation     用于日志与错误文案的操作名
     */
    public static SseEmitter fromFlux(Flux<String> flux, long timeoutMillis, String operation) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        AtomicReference<Disposable> subscription = new AtomicReference<>();
        AtomicBoolean closed = new AtomicBoolean(false);

        Disposable disposable = flux.subscribe(
                chunk -> send(emitter, closed, "message", "delta", chunk),
                error -> {
                    log.error("{} 流式调用失败", operation, error);
                    send(emitter, closed, "message", "delta",
                            "\n\n[服务异常] " + (error.getMessage() == null ? "请稍后重试" : error.getMessage()));
                    complete(emitter, closed);
                },
                () -> complete(emitter, closed));
        subscription.set(disposable);

        emitter.onCompletion(() -> {
            closed.set(true);
            dispose(subscription);
        });
        emitter.onTimeout(() -> {
            log.warn("{} SSE 超时，取消订阅", operation);
            closed.set(true);
            dispose(subscription);
            // SseEmitter.complete() 幂等；此处不能再走 complete() 的 compareAndSet（closed 已被置位）
            emitter.complete();
        });
        emitter.onError(error -> {
            closed.set(true);
            dispose(subscription);
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, AtomicBoolean closed,
                             String eventName, String field, String text) {
        if (closed.get() || text == null || text.isEmpty()) {
            return;
        }
        try {
            String payload = MAPPER.writeValueAsString(Map.of(field, text));
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (JsonProcessingException e) {
            log.warn("SSE 分片序列化失败", e);
        } catch (IOException | IllegalStateException e) {
            log.info("SSE 连接已关闭，停止发送：{}", e.getMessage());
            closed.set(true);
        }
    }

    private static void complete(SseEmitter emitter, AtomicBoolean closed) {
        if (closed.compareAndSet(false, true)) {
            emitter.complete();
        }
    }

    private static void dispose(AtomicReference<Disposable> subscription) {
        Disposable disposable = subscription.get();
        if (disposable != null && !disposable.isDisposed()) {
            disposable.dispose();
        }
    }
}
