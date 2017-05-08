package com.qivicon.backend.messaging.verticles;

import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.verticles.events.Events;
import com.qivicon.backend.messaging.verticles.websocket.WebSocketHandler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.qivicon.backend.messaging.verticles.events.Events.*;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class HttpServerVerticleReceiverTest extends BaseTest {

    private EventBus eventBus;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp(TestContext context) {
        super.setUp(context);
        eventBus = mock(EventBus.class);
        when(eventBus.consumer(Events.createOutboundMessageAddress(HOME_BASE_ID))).thenReturn(mock(MessageConsumer.class));

        HttpServerVerticle verticle = new HttpServerVerticle(new WebSocketHandler(eventBus));

        vertx.deployVerticle(verticle,
                context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
    }

    @Test(timeout = 10000)
    public void serverShouldReceiveMessage(TestContext context) {
        final Async async = context.async();
        connect(vertx, webSocket -> {
            LOG.info("WebSocket client connected");
            webSocket
                .writeTextMessage(MESSAGE_CONTENT_CLIENT.encode())
                .closeHandler(event -> {
                    LOG.info("WebSocket connection closed");
                    // Client connection should be closed gracefully
                    verify(eventBus).publish(WEBSOCKET_CONNECTION_OPENED, HOME_BASE_ID);
                    //verify(eventBus).send(WEBSOCKET_INBOUND_MESSAGE, MESSAGE_CONTENT_CLIENT);
                    verify(eventBus).publish(WEBSOCKET_CONNECTION_CLOSED, HOME_BASE_ID);
                    async.complete();
                })
                .exceptionHandler(exception -> {
                    LOG.warn("WebSocket failed");
                    context.fail(exception);
                })
                .close();
        });
    }

}
