package com.qivicon.backend.messaging.client.rabbitmq;

import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.client.MessagingClient;
import com.qivicon.backend.messaging.verticles.events.Events;
import com.rabbitmq.client.AMQP;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.qivicon.backend.messaging.client.rabbitmq.RabbitMQClient.CONFIG_PREXIX;

@RunWith(VertxUnitRunner.class)
public class RabbitMQClientTest extends BaseTest {


    private static final String TEST_MESSAGE_BODY = "TestBody";
    private MessagingClient client;
    private MetricRegistry metricRegistry;

    @ClassRule
    public static GenericContainer rabbitMQ =
            new GenericContainer("rabbitmq:3.6.9-management-alpine")
                .withExposedPorts(5671, 15671, 4369);

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        JsonObject config = new JsonObject()
                .put(CONFIG_PREXIX + "host", rabbitMQ.getContainerIpAddress())
                .put(CONFIG_PREXIX + "port", rabbitMQ.getMappedPort(5672))
                .put(CONFIG_PREXIX + "user", "guest")
                .put(CONFIG_PREXIX + "password", "guest");
        metricRegistry = new MetricRegistry();
        client = RabbitMQClientFactory.create(vertx, metricRegistry, config).get();

        waitUntilRabbitMQisStarted(context);
    }

    private void waitUntilRabbitMQisStarted(TestContext context) {
        ToStringConsumer toStringConsumer = new ToStringConsumer();
        WaitingConsumer waitingConsumer = new WaitingConsumer();
        Consumer<OutputFrame> composedConsumer = toStringConsumer.andThen(waitingConsumer);
        rabbitMQ.followOutput(composedConsumer);
        try {
            waitingConsumer.waitUntil(frame ->
                    frame.getUtf8String().contains("Server startup complete"), 30, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            context.fail(e);
        }
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
    }

    @Test
    public void shouldCreateConnection(TestContext context) {
        Async async = context.async();
        client.connect()
            .setHandler(messageCountEvent -> {
                if (messageCountEvent.succeeded()) {
                    long connectionCount = metricRegistry.counter("rabbitmq.connections").getCount();
                    LOG.info("Connection count: {}", connectionCount);
                    context.assertEquals(1L, connectionCount);
                    async.complete();
                } else {
                    context.fail(messageCountEvent.cause());
                }
            });
    }

    @Test
    public void shouldCreateQueue(TestContext context) {
        Async async = context.async();
        final String exchangeName = "exchangeName";
        final String queueName = "queueName";
        Future<Long> messageCountFuture = createExchangeAndBindToQueue(exchangeName, queueName, "")
                .compose(bindOk ->  client.messageCount(queueName));
                messageCountFuture.setHandler(messageCountEvent -> {
                    if (messageCountEvent.succeeded()) {
                        context.assertEquals(0L, messageCountEvent.result());
                        async.complete();
                    } else {
                        context.fail(messageCountEvent.cause());
                    }
                });
    }

    @Test
    public void shouldPublishAndConsumeQueue(TestContext context) {
        final String exchangeName = "test.exchangeName";
        final String queueName = "test.queueName";
        final String routingKey = "test.routingKey";

        Async async = context.async();
        MessageConsumer<JsonObject> eventBusForwarderConsumer = vertx.eventBus()
                .consumer(Events.WEBSOCKET_INBOUND_MESSAGE, messageEvent -> {
            LOG.info("Consumed message {}", messageEvent.body().encode());
            LOG.info("Message consumed");
            long consumedMessagesCount = metricRegistry.meter("rabbitmq.consumed").getCount();
            LOG.info("Consumed messages count: {}", consumedMessagesCount);
            context.assertEquals(TEST_MESSAGE_BODY, messageEvent.body().getString("body"));
            client.messageCount(queueName).setHandler(messageCountEvent -> {
                if (messageCountEvent.succeeded()) {
                    LOG.info("Message count: {}", messageCountEvent.result());
                    context.assertEquals(0L, messageCountEvent.result());
                    async.complete();
                } else {
                    context.fail(messageCountEvent.cause());
                }
            });
        });

        eventBusForwarderConsumer.completionHandler(consumerRegisteredEvent -> {
            if(consumerRegisteredEvent.succeeded()){
                LOG.info("Registered message consumer");
                Future<String> messageConsumerFuture = createExchangeAndBindToQueue(exchangeName, queueName, routingKey)
                        .compose(bindOk -> client.basicConsume(queueName, Events.WEBSOCKET_INBOUND_MESSAGE, "myConsumerTag"));
                messageConsumerFuture.setHandler(consumerTagEvent -> {
                    if (consumerTagEvent.succeeded()) {
                        context.assertEquals("myConsumerTag", consumerTagEvent.result());
                        client.basicPublish(exchangeName, routingKey, new JsonObject().put("body", TEST_MESSAGE_BODY))
                            .setHandler(publishMessageEvent -> {
                                if(publishMessageEvent.succeeded()){
                                    LOG.info("Message published");
                                    long publishedMessagesCount = metricRegistry.meter("rabbitmq.published").getCount();
                                    LOG.info("Published messages count: {}", publishedMessagesCount);
                                    context.assertEquals(1L, publishedMessagesCount);
                                }else{
                                    context.fail(publishMessageEvent.cause());
                                }
                            });
                    } else {
                        context.fail(consumerTagEvent.cause());
                    }
                });
            }
        });

    }

    public Future<AMQP.Queue.BindOk> createExchangeAndBindToQueue(String exchangeName, String queueName, String routingKey){
        AMQP.Exchange.Declare exchangeDeclare = new AMQP.Exchange.Declare.Builder()
                .exchange(exchangeName)
                .build();
       return client.connect()
                .compose(connection ->  client.exchangeDeclare(exchangeDeclare))
                .compose(exchangeOk -> {
                    AMQP.Queue.Declare queueDeclare = new AMQP.Queue.Declare.Builder()
                            .queue(queueName)
                            .build();
                    return client.queueDeclare(queueDeclare);
                })
                .compose(queueOk -> client.queueBind(
                        queueName,
                        exchangeName,
                        routingKey));
    }
}
