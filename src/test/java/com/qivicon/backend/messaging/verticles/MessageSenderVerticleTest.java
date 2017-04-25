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

import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class MessageSenderVerticleTest extends BaseTest {

    private MessagingService messagingService;

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        messagingService = Mockito.mock(MessagingService.class);
        when(messagingService.start()).thenReturn(Future.succeededFuture());
        when(messagingService.stop()).thenReturn(Future.succeededFuture());
        MessageSenderVerticle verticle = new MessageSenderVerticle(() -> messagingService);

        vertx.deployVerticle(verticle,
                context.asyncAssertSuccess());
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
    }

    @Test(timeout = 10000)
    public void shouldConsumeInboundMessage(TestContext context) {
        Async async = context.async();
        when(messagingService.processMessage(anyString(), anyObject())).then(invocation -> {
            LOG.info("Mock: MessagingService::processMessage invoked");
            async.complete();
            return Future.succeededFuture();
        });
        vertx.eventBus().send(Events.WEBSOCKET_INBOUND_MESSAGE, MESSAGE_CONTENT_CLIENT);
        async.await();
        verify(messagingService).processMessage(anyString(), anyObject());
    }
}
