package com.qivicon.backend.messaging.verticles;


import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.LoggingInterceptor;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.client.AmqpConnection;
import com.qivicon.backend.messaging.services.impl.DefaultMessagingService;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import okhttp3.*;
import org.junit.After;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.ToStringConsumer;
import org.testcontainers.containers.output.WaitingConsumer;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Consumer;

import static com.qivicon.backend.messaging.services.impl.DefaultMessagingService.CONFIG_PREXIX;

@RunWith(VertxUnitRunner.class)
public class ApplicationVerticleTest extends BaseTest {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationVerticleTest.class);
    private static final String HOST = "localhost:8080";
    private OkHttpClient httpClient;
    private AmqpClient amqpClient;

    @ClassRule
    public static GenericContainer rabbitMQ =
            new GenericContainer("rabbitmq:3.6.9-management-alpine")
                    .withExposedPorts(5671, 15671, 4369);

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        System.setProperty(CONFIG_PREXIX + "HOST", rabbitMQ.getContainerIpAddress());
        System.setProperty(CONFIG_PREXIX + "PORT", rabbitMQ.getMappedPort(5672).toString());
        System.setProperty(CONFIG_PREXIX + "USER", "guest");
        System.setProperty(CONFIG_PREXIX + "PASSWORD", "guest");

        waitUntilRabbitMQisStarted(context);

        ApplicationVerticle verticle = new ApplicationVerticle();

        vertx.deployVerticle(verticle,
                context.asyncAssertSuccess());

        httpClient = new OkHttpClient.Builder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .readTimeout(0,  TimeUnit.MILLISECONDS)
                .build();

        MetricRegistry metricRegistry = new MetricRegistry();
        amqpClient = AmqpClient.create(vertx, metricRegistry);
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
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        httpClient.dispatcher().executorService().shutdown();
        amqpClient.close();
    }

    @Test
    public void shouldConsumeMessageFromHomeBaseQueue(TestContext context) throws TimeoutException, InterruptedException {
        String credentials = Credentials.basic("HomebaseID", "HomebasePWD");
        Request request = new Request.Builder()
                .url("ws://" + HOST + "/messaging")
                .addHeader("Authorization", credentials)
                .build();
        Async async = context.async();
        WebSocket ws = httpClient.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket ws, Response response) {
                LOG.info("Connected");
            }

            @Override public void onMessage(WebSocket webSocket, String text) {
                LOG.info("Message received: " + text);
                async.complete();
            }

            @Override public void onClosing(WebSocket ws, int code, String reason) {
                LOG.info("CLOSE: " + code + " " + reason);
            }

            @Override public void onFailure(WebSocket ws, Throwable exception, Response response) {
                context.fail(exception);
            }
        });
        Thread.sleep(1000);
        amqpClient.connect(rabbitMQ.getContainerIpAddress(), rabbitMQ.getMappedPort(5672))
            .compose(AmqpConnection::createChannel)
            .compose(channel -> channel.basicPublish(DefaultMessagingService.TO_GATEWAY_EXCHANGE, HOME_BASE_ID,
                    MESSAGE_CONTENT_BACKEND))
                .setHandler(event -> {
                    if(event.succeeded()){
                        LOG.info("Message published to queue");
                    }else{
                        context.fail(event.cause());
                    }
                });
        async.await(10000);
    }
}
