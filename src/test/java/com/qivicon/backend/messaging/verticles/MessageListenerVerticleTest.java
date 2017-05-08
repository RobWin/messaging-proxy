package com.qivicon.backend.messaging.verticles;


import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.verticles.events.Events;
import com.qivicon.backend.messaging.services.MessagingService;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;

import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class MessageListenerVerticleTest extends BaseTest {

    private MessagingService messagingService;

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        messagingService = Mockito.mock(MessagingService.class);
        when(messagingService.start()).thenReturn(Future.succeededFuture());
        when(messagingService.stop()).thenReturn(Future.succeededFuture());
        MessageListenerVerticle verticle = new MessageListenerVerticle(messagingService);
        vertx.deployVerticle(verticle,
                context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
    }

    @Test(timeout = 10000)
    public void shouldConsumeConnectionOpenedEvent(TestContext context) {
        Async async = context.async();
        //Given
        doAnswer(invocation -> {
            LOG.info("Mock: MessagingService::onClientConnect invoked");
            async.complete();
            return null;
        }).when(messagingService).onClientConnect(anyObject());
        //When
        vertx.eventBus().send(Events.WEBSOCKET_CONNECTION_OPENED, HOME_BASE_ID);
        //Then
        async.await();
        verify(messagingService).onClientConnect(HOME_BASE_ID);
    }

    @Test(timeout = 10000)
    public void shouldConsumeConnectionClosedEvent(TestContext context) {
        Async async = context.async();
        //Given
        doAnswer(invocation -> {
            LOG.info("Mock: MessagingService::onClientDisconnect invoked");
            async.complete();
            return null;
        }).when(messagingService).onClientDisconnect(anyObject());
        //When
        vertx.eventBus().send(Events.WEBSOCKET_CONNECTION_CLOSED, HOME_BASE_ID);
        //Then
        async.await();
        verify(messagingService).onClientDisconnect(HOME_BASE_ID);
    }
}
