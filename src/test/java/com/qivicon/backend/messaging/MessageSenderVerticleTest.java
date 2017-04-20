package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.events.Events;
import com.qivicon.backend.messaging.rabbitmq.MessagingService;
import com.qivicon.backend.messaging.verticles.MessageSenderVerticle;
import io.vertx.core.Vertx;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@RunWith(VertxUnitRunner.class)
public class MessageSenderVerticleTest extends BaseVerticleTest {

    private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticleTest.class);


    private Vertx vertx;
    private MessagingService messagingService;

    @Before
    public void setUp(TestContext context) {
        LOG.info("Start Vertx");
        vertx = Vertx.vertx();
        messagingService = Mockito.mock(MessagingService.class);
        MessageSenderVerticle verticle = new MessageSenderVerticle(messagingService);

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
    public void shouldConsumeInboundMessage(TestContext context) {
        Async async = context.async();
        doAnswer(invocation -> {
            LOG.info("Mock: MessagingService::processMessage invoked");
            async.complete();
            return null;
        }).when(messagingService).processMessage(anyString());
        vertx.eventBus().send(Events.WEBSOCKET_INBOUND_MESSAGE, MESSAGE_CONTENT);
        async.await();
        verify(messagingService).processMessage(MESSAGE_CONTENT);
    }
}
