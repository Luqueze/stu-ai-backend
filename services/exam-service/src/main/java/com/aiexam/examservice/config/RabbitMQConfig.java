package com.aiexam.examservice.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "exam.generation.exchange";
    public static final String ROUTING_KEY_REQUESTED = "exam.generation.requested";
    public static final String ROUTING_KEY_COMPLETED = "exam.generation.completed";
    public static final String ROUTING_KEY_FAILED = "exam.generation.failed";

    public static final String QUEUE_COMPLETED = "exam.generation.completed.queue";
    public static final String QUEUE_FAILED = "exam.generation.failed.queue";

    private static final String DLX = "exam.generation.dlx";
    private static final String QUEUE_COMPLETED_DLQ = QUEUE_COMPLETED + ".dlq";
    private static final String QUEUE_FAILED_DLQ = QUEUE_FAILED + ".dlq";

    @Bean
    public MessageConverter messageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public TopicExchange examGenerationExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public DirectExchange examGenerationDeadLetterExchange() {
        return new DirectExchange(DLX, true, false);
    }

    @Bean
    public Queue examGenerationCompletedQueue() {
        return QueueBuilder.durable(QUEUE_COMPLETED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_COMPLETED)
                .build();
    }

    @Bean
    public Queue examGenerationFailedQueue() {
        return QueueBuilder.durable(QUEUE_FAILED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_FAILED)
                .build();
    }

    @Bean
    public Queue examGenerationCompletedDlq() {
        return QueueBuilder.durable(QUEUE_COMPLETED_DLQ).build();
    }

    @Bean
    public Queue examGenerationFailedDlq() {
        return QueueBuilder.durable(QUEUE_FAILED_DLQ).build();
    }

    @Bean
    public Binding completedBinding(Queue examGenerationCompletedQueue, TopicExchange examGenerationExchange) {
        return BindingBuilder.bind(examGenerationCompletedQueue)
                .to(examGenerationExchange)
                .with(ROUTING_KEY_COMPLETED);
    }

    @Bean
    public Binding failedBinding(Queue examGenerationFailedQueue, TopicExchange examGenerationExchange) {
        return BindingBuilder.bind(examGenerationFailedQueue).to(examGenerationExchange).with(ROUTING_KEY_FAILED);
    }

    @Bean
    public Binding completedDlqBinding(
            Queue examGenerationCompletedDlq, DirectExchange examGenerationDeadLetterExchange) {
        return BindingBuilder.bind(examGenerationCompletedDlq)
                .to(examGenerationDeadLetterExchange)
                .with(QUEUE_COMPLETED);
    }

    @Bean
    public Binding failedDlqBinding(Queue examGenerationFailedDlq, DirectExchange examGenerationDeadLetterExchange) {
        return BindingBuilder.bind(examGenerationFailedDlq)
                .to(examGenerationDeadLetterExchange)
                .with(QUEUE_FAILED);
    }
}
