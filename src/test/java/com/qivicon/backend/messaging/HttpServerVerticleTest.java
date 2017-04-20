package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.verticles.HttpServerVerticle;
import com.qivicon.backend.messaging.websocket.WebSocketHandler;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.WebSocket;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.events.Events.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class HttpServerVerticleTest extends BaseVerticleTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticleTest.class);

    private Vertx vertx;
    private EventBus eventBus;

    @Before
    public void setUp(TestContext context) {
        LOG.info("Start Vertx");
        vertx = Vertx.vertx();
        eventBus = mock(EventBus.class);
        HttpServerVerticle verticle = new HttpServerVerticle(new WebSocketHandler(eventBus));

        vertx.deployVerticle(verticle,
                context.asyncAssertSuccess());

        // Report uncaught exceptions as Vert.x Unit failures
        vertx.exceptionHandler(context.exceptionHandler());
        
    }

    @After
    public void tearDown(TestContext context) {
        LOG.info("Stop Vertx");
        vertx.close(context.asyncAssertSuccess());
    }

    @Test(timeout = 10000)
    public void shouldConnectToWebSocketServer(TestContext context) {
        final Async async = context.async();
        connectWebSocketClient(vertx, webSocket -> {
            LOG.info("WebSocket client connected");
            handleSocket(context, async, webSocket);
        });
    }

    private void handleSocket(TestContext context, Async async, WebSocket socket) {
        socket.writeTextMessage(MESSAGE_CONTENT)
            .closeHandler(event -> {
                LOG.info("WebSocket connection closed");
                // Client connection should be closed gracefully

                verify(eventBus).publish(WEBSOCKET_CONNECTION_OPENED, HOME_BASE_ID);
                verify(eventBus).send(WEBSOCKET_INBOUND_MESSAGE, MESSAGE_CONTENT);
                verify(eventBus).publish(WEBSOCKET_CONNECTION_CLOSED, HOME_BASE_ID);
                async.complete();
            })
            .exceptionHandler(exception -> {
                LOG.warn("WebSocket connection failed");
                context.fail(exception);
            }).close();
    }

}
