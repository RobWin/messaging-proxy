package com.qivicon.backend.messaging.verticles;

import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.verticles.events.Events;
import com.qivicon.backend.messaging.verticles.websocket.WebSocketHandler;
import io.netty.handler.codec.http.websocketx.WebSocketHandshakeException;
import io.vertx.core.eventbus.EventBus;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

@RunWith(VertxUnitRunner.class)
public class HttpServerVerticleSenderTest extends BaseTest {

    private EventBus eventBus;

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        eventBus = vertx.eventBus();
        HttpServerVerticle verticle = new HttpServerVerticle(new WebSocketHandler(eventBus));
        vertx.deployVerticle(verticle,
                context.asyncAssertSuccess());
        
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
    }

    @Test(timeout = 10000)
    public void shouldConsumeAndSendOutboundMessage(TestContext context) {
        final Async async = context.async();
        connect(vertx, webSocket -> {
            LOG.info("WebSocket client connected");
            eventBus.sender(Events.createOutboundMessageAddress(HOME_BASE_ID))
                .exceptionHandler(e -> LOG.warn("Failed to send message", e))
                .send(MESSAGE_CONTENT_BACKEND);
            webSocket
                    .handler(message -> {
                        LOG.info("Received message by server: {}", message.toString());
                        context.assertEquals(MESSAGE_CONTENT_BACKEND, message.toJsonObject());
                        webSocket.close();
                    })
                    .closeHandler(event -> {
                        LOG.info("WebSocket connection closed");
                        // Client connection should be closed gracefully
                        async.complete();
                    })
                    .exceptionHandler(exception -> {
                        LOG.warn("WebSocket failed");
                        context.fail(exception);
                    });
        });
    }

    @Test(timeout = 10000)
    public void shouldFailToConnect(TestContext context) {
        final Async async = context.async();
        connectWithoutCredentials(vertx, exception -> {
            assertThat(exception).isInstanceOf(WebSocketHandshakeException.class)
                    .hasMessageContaining("401");
            async.complete();
        });
    }

}
