package com.exam_bank.exam_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "exam.events";
    public static final String ROUTING_KEY = "exam.submitted";
    public static final String QUEUE = "exam.events.queue";

    @Bean
    public Queue examEventsQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding examEventsBinding(Queue examEventsQueue, TopicExchange examEventsExchange) {
        return BindingBuilder.bind(examEventsQueue)
                .to(examEventsExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public TopicExchange examEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public JacksonJsonMessageConverter jacksonJsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory,
            JacksonJsonMessageConverter messageConverter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter);
        return template;
    }
}
