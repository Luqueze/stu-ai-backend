package com.aiexam.aigeneratorservice.config;

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

    public static final String QUEUE_REQUESTED = "exam.generation.requested.queue";

    private static final String DLX = "exam.generation.dlx";
    private static final String QUEUE_REQUESTED_DLQ = QUEUE_REQUESTED + ".dlq";

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
    public Queue examGenerationRequestedQueue() {
        return QueueBuilder.durable(QUEUE_REQUESTED)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", QUEUE_REQUESTED)
                .build();
    }

    @Bean
    public Queue examGenerationRequestedDlq() {
        return QueueBuilder.durable(QUEUE_REQUESTED_DLQ).build();
    }

    @Bean
    public Binding requestedBinding(Queue examGenerationRequestedQueue, TopicExchange examGenerationExchange) {
        return BindingBuilder.bind(examGenerationRequestedQueue)
                .to(examGenerationExchange)
                .with(ROUTING_KEY_REQUESTED);
    }

    @Bean
    public Binding requestedDlqBinding(
            Queue examGenerationRequestedDlq, DirectExchange examGenerationDeadLetterExchange) {
        return BindingBuilder.bind(examGenerationRequestedDlq)
                .to(examGenerationDeadLetterExchange)
                .with(QUEUE_REQUESTED);
    }
}
