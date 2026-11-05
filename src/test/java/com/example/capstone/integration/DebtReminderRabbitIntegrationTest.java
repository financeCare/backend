package com.example.capstone.integration;

import com.example.capstone.messaging.DebtReminderListener;
import com.example.capstone.messaging.DebtReminderMessage;
import com.example.capstone.messaging.RabbitDebtReminderPublisher;
import com.example.capstone.messaging.ReminderMessagingConfig;
import com.example.capstone.service.NotificationService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.amqp.rabbit.annotation.EnableRabbit;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/** The due-date reminder topology against a real RabbitMQ: delivery, retries and dead-lettering (skipped without Docker). */
@Testcontainers(disabledWithoutDocker = true)
class DebtReminderRabbitIntegrationTest {

    @Container
    static GenericContainer<?> rabbit = new GenericContainer<>(DockerImageName.parse("rabbitmq:3.13-alpine"))
            .withExposedPorts(5672)
            .waitingFor(org.testcontainers.containers.wait.strategy.Wait.forLogMessage(".*Server startup complete.*", 1));

    static NotificationService notificationService = mock(NotificationService.class);
    static AnnotationConfigApplicationContext context;
    static RabbitTemplate template;

    @Configuration
    @EnableRabbit
    @Import({ReminderMessagingConfig.class, DebtReminderListener.class, RabbitDebtReminderPublisher.class})
    static class TestConfig {

        @Bean
        CachingConnectionFactory connectionFactory() {
            return new CachingConnectionFactory("localhost", rabbit.getMappedPort(5672));
        }

        @Bean
        MessageConverter amqpMessageConverter() {
            return new Jackson2JsonMessageConverter();
        }

        @Bean
        RabbitAdmin rabbitAdmin(CachingConnectionFactory cf) {
            return new RabbitAdmin(cf);
        }

        @Bean
        RabbitTemplate rabbitTemplate(CachingConnectionFactory cf, MessageConverter converter) {
            RabbitTemplate t = new RabbitTemplate(cf);
            t.setMessageConverter(converter);
            return t;
        }

        @Bean
        NotificationService notificationService() {
            return notificationService;
        }

        /** Same behaviour as spring.rabbitmq.listener.simple.retry.* in application.properties, with short intervals. */
        @Bean
        SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
                CachingConnectionFactory cf, MessageConverter converter) {
            SimpleRabbitListenerContainerFactory f = new SimpleRabbitListenerContainerFactory();
            f.setConnectionFactory(cf);
            f.setMessageConverter(converter);
            f.setDefaultRequeueRejected(false);
            f.setAdviceChain(RetryInterceptorBuilder.stateless()
                    .maxAttempts(3)
                    .backOffOptions(50, 2, 500)
                    .recoverer(new RejectAndDontRequeueRecoverer())
                    .build());
            return f;
        }
    }

    @BeforeAll
    static void start() {
        context = new AnnotationConfigApplicationContext();
        org.springframework.boot.test.util.TestPropertyValues.of("app.notifications.async.enabled=true").applyTo(context);
        context.register(TestConfig.class);
        context.refresh();
        template = context.getBean(RabbitTemplate.class);
    }

    @AfterAll
    static void stop() {
        if (context != null) {
            context.close();
        }
    }

    @Test
    void publishedReminderIsDeliveredToTheService() {
        UUID user = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        UUID debt = UUID.randomUUID();

        context.getBean(RabbitDebtReminderPublisher.class).publish(new DebtReminderMessage(user, rule, debt, 3));

        verify(notificationService, timeout(10_000)).deliverDebtReminder(user, rule, debt, 3);
    }

    @Test
    void aReminderThatKeepsFailingIsRetriedThenDeadLettered() {
        UUID user = UUID.randomUUID();
        UUID rule = UUID.randomUUID();
        UUID debt = UUID.randomUUID();
        doThrow(new IllegalStateException("db down")).when(notificationService)
                .deliverDebtReminder(user, rule, debt, 0);

        context.getBean(RabbitDebtReminderPublisher.class).publish(new DebtReminderMessage(user, rule, debt, 0));

        // 3 attempts by the retry interceptor, then the message goes to reminder.debt.dlq. The DLQ listener
        // consumes it right away, so what we can observe is that the main queue drains and the service saw 3 calls.
        verify(notificationService, timeout(10_000).times(3)).deliverDebtReminder(user, rule, debt, 0);
    }
}
