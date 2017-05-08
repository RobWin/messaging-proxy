package com.qivicon.backend.messaging.client;

import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.BaseTest;
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

@RunWith(VertxUnitRunner.class)
public class AmqpClientTest extends BaseTest {

    private AmqpClient client;
    private MetricRegistry metricRegistry;
    private static final String TEST_MESSAGE_BODY = "TestBody";

    @ClassRule
    public static GenericContainer rabbitMQ =
            new GenericContainer("rabbitmq:3.6.9-management-alpine")
                    .withExposedPorts(5672, 15671, 4369);

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        metricRegistry = new MetricRegistry();
        client = AmqpClient.create(vertx, metricRegistry);
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
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

    @Test
    public void shouldConnect(TestContext context){
        waitUntilRabbitMQisStarted(context);
        Async async = context.async();
        client.connect(rabbitMQ.getContainerIpAddress(),
                        rabbitMQ.getMappedPort(5672))
            .setHandler(connectionEvent -> {
                if (connectionEvent.succeeded()) {
                    async.complete();
                }else{
                    context.fail(connectionEvent.cause());
                }
            });
    }

    @Test
    public void shouldCloseConnection(TestContext context){
        waitUntilRabbitMQisStarted(context);
        Async async = context.async(2);
        client.connect(rabbitMQ.getContainerIpAddress(),
                rabbitMQ.getMappedPort(5672))
                .compose(connection -> {
                    connection.closeHandler(event -> async.countDown());
                    return connection.close();
                })
                .setHandler(connectionClosedEvent -> {
                    if (connectionClosedEvent.succeeded()) {
                        async.countDown();
                    }else{
                        context.fail(connectionClosedEvent.cause());
                    }
                });
    }

    /*
    @Test
    public void shouldReConnect(TestContext context){
        Async async = context.async();
        client.connect(rabbitMQ.getContainerIpAddress(),
                rabbitMQ.getMappedPort(5672))
            .setHandler(connectionEvent -> {
                if (connectionEvent.succeeded()) {
                    AmqpConnectionImpl connection = (AmqpConnectionImpl)connectionEvent.result();
                    assertThat(connection.getRetryAttempts()).isGreaterThan(0);
                    async.complete();
                }else{
                    context.fail(connectionEvent.cause());
                }
            });
    }
    */

    @Test
    public void shouldCreateChannel(TestContext context){
        waitUntilRabbitMQisStarted(context);
        Async async = context.async();
        client.connect(rabbitMQ.getContainerIpAddress(),
                rabbitMQ.getMappedPort(5672))
                .compose(AmqpConnection::createChannel)
                .setHandler(channelEvent -> {
                    if (channelEvent.succeeded()) {
                        async.complete();
                    }else{
                        context.fail(channelEvent.cause());
                    }
                });
    }

    @Test
    public void shouldCloseChannel(TestContext context){
        waitUntilRabbitMQisStarted(context);
        Async async = context.async();
        client.connect(rabbitMQ.getContainerIpAddress(),
            rabbitMQ.getMappedPort(5672))
            .compose(AmqpConnection::createChannel)
            .compose(AmqpChannel::close)
            .setHandler(channelEvent -> {
                if (channelEvent.succeeded()) {
                    async.complete();
                }else{
                    context.fail(channelEvent.cause());
                }
            });
    }

    @Test
    public void shouldCloseConnectionAndChannel(TestContext context){
        waitUntilRabbitMQisStarted(context);
        Async async = context.async(2);
        client.connect(rabbitMQ.getContainerIpAddress(),
                rabbitMQ.getMappedPort(5672))
                .compose(AmqpConnection::createChannel)
                .compose(amqpChannel -> {
                    amqpChannel.closeHandler(event -> async.countDown());
                    return amqpChannel.getConnection().close();
                })
                .setHandler(channelEvent -> {
                    if (channelEvent.succeeded()) {
                        async.countDown();
                    }else{
                        context.fail(channelEvent.cause());
                    }
                });
    }


    @Test
    public void shouldCreateExchangeQueueAndBinding(TestContext context) {
        waitUntilRabbitMQisStarted(context);
        Async async = context.async();
        final String exchangeName = "test.exchangeName";
        final String queueName = "test.queueName";
        final String routingKey = "test.routingKey";

        createExchangeAndBindToQueue(exchangeName, queueName, routingKey)
            .setHandler(result -> {
                if(result.succeeded()){
                    async.complete();
                }else{
                    context.fail(result.cause());
                }
            });
    }

    @Test
    public void shouldPublishAndConsumeQueue(TestContext context) {
        waitUntilRabbitMQisStarted(context);
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
                    async.complete();
                });

        eventBusForwarderConsumer.completionHandler(consumerRegisteredEvent -> {
            if(consumerRegisteredEvent.succeeded()){
                LOG.info("Registered message consumer");
                createExchangeAndBindToQueue(exchangeName, queueName, routingKey)
                        .compose(channel  -> channel.basicConsume(queueName, Events.WEBSOCKET_INBOUND_MESSAGE, "myConsumerTag"))
                    .setHandler(consumerTagEvent -> {
                        if (consumerTagEvent.succeeded()) {
                            createChannel()
                                    .compose(channel -> channel.basicPublish(exchangeName, routingKey, new JsonObject().put("body", TEST_MESSAGE_BODY))
                                    .setHandler(publishMessageEvent -> {
                                        if(publishMessageEvent.succeeded()){
                                            LOG.info("Message published");
                                            long publishedMessagesCount = metricRegistry.meter("rabbitmq.published").getCount();
                                            LOG.info("Published messages count: {}", publishedMessagesCount);
                                            context.assertEquals(1L, publishedMessagesCount);
                                        }else{
                                            context.fail(publishMessageEvent.cause());
                                        }
                                    }));
                        } else {
                            context.fail(consumerTagEvent.cause());
                        }
                    });
            }
        });

    }

    private Future<AmqpChannel> createExchangeAndBindToQueue(String exchangeName, String queueName, String routingKey) {
        return createChannel()
                .compose(channel -> createExchange(channel, exchangeName))
                .compose(channel -> createQueue(channel, queueName))
                .compose(channel -> bindQueue(channel, exchangeName, queueName, routingKey));
    }

    public Future<AmqpChannel> createChannel() {
        return client.connect(rabbitMQ.getContainerIpAddress(),
                rabbitMQ.getMappedPort(5672))
                .compose(AmqpConnection::createChannel);
    }

    public Future<AmqpChannel> createExchange(AmqpChannel channel, String exchangeName) {
        AMQP.Exchange.Declare exchangeDeclare = new AMQP.Exchange.Declare.Builder()
                .exchange(exchangeName)
                .build();
        return channel.exchangeDeclare(exchangeDeclare);
    }

    public Future<AmqpChannel> createQueue(AmqpChannel channel, String queueName) {
        AMQP.Queue.Declare queueDeclare = new AMQP.Queue.Declare.Builder()
                .queue(queueName)
                .build();
        return channel.queueDeclare(queueDeclare);
    }

    public Future<AmqpChannel> bindQueue(AmqpChannel channel, String exchangeName, String queueName, String routingKey) {
        return channel.queueBind(
                queueName,
                exchangeName,
                routingKey);
    }
}
