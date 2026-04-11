package com.exam_bank.exam_service.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "exam.events";
    public static final String ROUTING_KEY = "exam.submitted";
    public static final String QUEUE = "exam.events.queue";

    public static final String AUTH_EVENTS_EXCHANGE = "auth.events";
    public static final String AUTH_PROFILE_SYNC_ROUTING_KEY = "auth.user.profile.sync";
    public static final String AUTH_PROFILE_SYNC_QUEUE = "exam.auth.user-profile-sync.queue";

    @Bean
    public Queue examEventsQueue() {
        return new Queue(QUEUE, true);
    }

    @Bean
    public Binding examEventsBinding(
            @Qualifier("examEventsQueue") Queue examEventsQueue,
            @Qualifier("examEventsExchange") TopicExchange examEventsExchange) {
        return BindingBuilder.bind(examEventsQueue)
                .to(examEventsExchange)
                .with(ROUTING_KEY);
    }

    @Bean
    public TopicExchange examEventsExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public TopicExchange authEventsExchange(
            @Value("${auth.events.exchange:" + AUTH_EVENTS_EXCHANGE + "}") String exchangeName) {
        return new TopicExchange(exchangeName, true, false);
    }

    @Bean
    public Queue authUserProfileSyncQueue(
            @Value("${auth.events.user-profile-sync.queue:" + AUTH_PROFILE_SYNC_QUEUE + "}") String queueName) {
        return new Queue(queueName, true);
    }

    @Bean
    public Binding authUserProfileSyncBinding(
            @Qualifier("authUserProfileSyncQueue") Queue authUserProfileSyncQueue,
            @Qualifier("authEventsExchange") TopicExchange authEventsExchange,
            @Value("${auth.events.user-profile-sync-routing-key:" + AUTH_PROFILE_SYNC_ROUTING_KEY
                    + "}") String routingKey) {
        return BindingBuilder.bind(authUserProfileSyncQueue)
                .to(authEventsExchange)
                .with(routingKey);
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
