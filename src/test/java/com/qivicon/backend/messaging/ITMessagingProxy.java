package com.qivicon.backend.messaging;


import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.client.AmqpChannel;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.client.AmqpConnection;
import com.qivicon.backend.messaging.services.impl.DefaultMessagingService;
import com.rabbitmq.client.AMQP;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import net.jodah.concurrentunit.Waiter;
import okhttp3.*;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class ITMessagingProxy {

    private static final Logger LOG = LoggerFactory.getLogger(ITMessagingProxy.class);
    //public static final String HOST = "messaging-proxy-myproject.192.168.3.60.xip.io";
    public static final String HOST = "localhost:8080";

    private static final String MESSAGE_CONTENT_CLIENT = "{\"body\":\"Hello from client\"}";
    protected static final JsonObject MESSAGE_CONTENT_BACKEND = new JsonObject("{\"body\":\"Hello from backend\"}");
    public static final int NUMBER_OF_HOMEBASES = 10000;
    private AmqpClient amqpClient;
    private OkHttpClient client;

    @Before
    public void setUp() {
        MetricRegistry metricRegistry = new MetricRegistry();
        amqpClient = AmqpClient.create(Vertx.vertx(), metricRegistry);

        Dispatcher dispatcher = new Dispatcher();
        dispatcher.setMaxRequestsPerHost(NUMBER_OF_HOMEBASES);
        dispatcher.setMaxRequests(NUMBER_OF_HOMEBASES);

        client = new OkHttpClient.Builder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .readTimeout(5,  TimeUnit.SECONDS)
                .connectTimeout(5,  TimeUnit.SECONDS)
                .dispatcher(dispatcher)
                .build();
    }

    @After
    public void tearDown() {
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        //client.dispatcher().executorService().shutdown();
    }

    @Test
    public void shouldConnectManyHomeBases() throws TimeoutException, InterruptedException {
        final Waiter connectionWaiter = new Waiter();
        final Waiter messageWaiter = new Waiter();
        final AtomicInteger queueCount = new AtomicInteger(0);
        final AtomicInteger messageCount = new AtomicInteger(0);

        IntStream.range(0, NUMBER_OF_HOMEBASES).parallel().forEach(id -> {
            connect("HomeBase" + id, connectionWaiter, messageWaiter, queueCount, messageCount);
        });
        connectionWaiter.await(1000000, NUMBER_OF_HOMEBASES);

        Thread.sleep(10000);

        amqpClient.connect("localhost")
                .compose(AmqpConnection::createChannel)
                .setHandler(event -> {
            if (event.succeeded()) {
                IntStream.range(0, NUMBER_OF_HOMEBASES).parallel().forEach(id -> {
                    sendMessage(event.result(), "HomeBase" + id);
                });
            }
        });

        // Wait for all messages
        messageWaiter.await(1000000, NUMBER_OF_HOMEBASES);

    }

    @Test
    public void shouldConnectManyBackends() throws TimeoutException, InterruptedException {
        final Waiter connectionWaiter = new Waiter();
        final AtomicInteger queueCount = new AtomicInteger(0);
        IntStream.range(0, 1000).parallel().forEach(id -> {
            createExchangeAndBindToQueue(id)
                .setHandler(event -> {
                    if (event.succeeded()) {
                        queueCount.incrementAndGet();
                        LOG.info("Connected {} #{}", id, queueCount.intValue());
                        sendMessage(event.result(), "HomeBase" + id);
                        connectionWaiter.resume();
                    }
                });
        });

        // Wait for all messages
        connectionWaiter.await(1000000, 1000);
    }

    private WebSocket connect(String homebaseId, Waiter connectionWaiter, Waiter messageWaiter, AtomicInteger queueCount, AtomicInteger messageCount) {
        String credentials = Credentials.basic(homebaseId, "rabbitmq");
        Request request = new Request.Builder()
                .url("ws://" + HOST + "/messaging")
                .addHeader("Authorization", credentials)
                .build();
        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                queueCount.incrementAndGet();
                LOG.info("Connected {} #{}", homebaseId, queueCount.intValue());
                connectionWaiter.resume();
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                messageCount.incrementAndGet();

                LOG.info("Received message #{}", messageCount.intValue());
                messageWaiter.resume();
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                LOG.info("CLOSE: " + code + " " + reason);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable exception, Response response) {
                connectionWaiter.rethrow(exception);
            }
        });
        return ws;
    }

    private void sendMessage(AmqpChannel channel, String homeBaseId){
        channel.basicPublish(DefaultMessagingService.TO_GATEWAY_EXCHANGE, homeBaseId,
                MESSAGE_CONTENT_BACKEND);
    }

    private Future<AmqpChannel> createExchangeAndBindToQueue(int id) {
        final String exchangeName = "to_gateway.direct";
        final String queueName = "HomeBase" + id;
        final String routingKey = "HomeBase" + id;

        return amqpClient.connect("localhost")
                .compose(AmqpConnection::createChannel)
                .compose(channel -> createExchange(channel, "to_gateway.direct"))
                .compose(channel -> createQueue(channel, queueName))
                .compose(channel -> bindQueue(channel, exchangeName, queueName, routingKey));
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
