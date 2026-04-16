package com.prodigal.travel.config.rabbitmq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.retry.support.RetryTemplateBuilder;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 用户 - MQ 配置
 * @since 2026/4/15
 */
@Slf4j
@Configuration
@ConditionalOnProperty(name = "spring.rabbitmq.enabled", havingValue = "true", matchIfMissing = true)
public class UserRabbiMQConfig {

    // 交换机常量
    public static final String USER_EXCHANGE = "prodigal.user.exchange";
    // 队列常量
    public static final String USER_REGISTERED_EMAIL_QUEUE = "prodigal.user.registered.queue";

    @Bean(USER_EXCHANGE)
    public Exchange userExchange() {
        return  ExchangeBuilder.topicExchange(USER_EXCHANGE)
                .durable(true)
                .build();
    }

    @Bean(USER_REGISTERED_EMAIL_QUEUE)
    public Queue userRegisteredEmailQueue() {
        return QueueBuilder.durable(USER_REGISTERED_EMAIL_QUEUE).build();
    }

    @Bean
    public Binding userRegisteredEmailBinding(@Qualifier(USER_REGISTERED_EMAIL_QUEUE)  Queue queue,
                                              @Qualifier(USER_EXCHANGE)  Exchange exchange) {
        return BindingBuilder.bind(queue)
                .to(exchange)
                .with(USER_REGISTERED_EMAIL_QUEUE)
                .noargs();
    }

    /**
     *
     * @param connectionFactory
     * @param jsonMessageConverter
     * @return
     */
    @Bean
    public SimpleRabbitListenerContainerFactory userRabbitListenerContainerFactory(
            ConnectionFactory connectionFactory, MessageConverter jsonMessageConverter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setConcurrentConsumers(10);
        factory.setTaskExecutor(Executors.newVirtualThreadPerTaskExecutor());
        factory.setAcknowledgeMode(AcknowledgeMode.AUTO);
        factory.setMessageConverter(jsonMessageConverter);
        RetryTemplate retryTemplate = new RetryTemplateBuilder()
                .maxAttempts(3)
                .exponentialBackoff(Duration.ofSeconds(2), 1.5, Duration.ofSeconds(30))
                .build();
        factory.setRetryTemplate(retryTemplate);
        factory.setErrorHandler(e -> log.warn("[user register send email] Error: {}", e.getMessage(), e));
        return factory;
    }
}
