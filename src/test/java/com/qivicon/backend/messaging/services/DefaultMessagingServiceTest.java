package com.qivicon.backend.messaging.services;

import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.client.MessagingClient;
import com.qivicon.backend.messaging.services.impl.DefaultMessagingService;
import io.vertx.core.Future;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.mockito.Mockito.*;

@RunWith(VertxUnitRunner.class)
public class DefaultMessagingServiceTest extends BaseTest {

    private MessagingService messagingService;
    private MessagingClient client;

    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        client = mock(MessagingClient.class);
        when(client.connect()).thenReturn(Future.succeededFuture());
        when(client.disconnect()).thenReturn(Future.succeededFuture());
        messagingService = new DefaultMessagingService(() -> client);
    }

    @After
    public void tearDown(TestContext context) {
        super.tearDown(context);
    }

    @Test(timeout = 10000)
    public void shouldStartClient(TestContext context) {
        Async async = context.async();
        Future<Void> startFuture = messagingService.start();
        startFuture.setHandler(startEvent -> {
            if(startEvent.succeeded()){
                async.complete();
            }else{
                context.fail(startEvent.cause());
            }
        });
    }

    @Test(timeout = 10000)
    public void shouldStopClient(TestContext context) {
        Async async = context.async();
        Future<Void> stopFuture = messagingService.stop();
        stopFuture.setHandler(stopEvent -> {
            if(stopEvent.succeeded()){
                async.complete();
            }else{
                context.fail(stopEvent.cause());
            }
        });
    }

    @Test(timeout = 10000)
    public void shouldPublishMessage(TestContext context) {
        //Given
        when(client.basicPublish(anyString(), anyString(), anyObject())).thenReturn(Future.succeededFuture());

        //When
        Async async = context.async();
        messagingService.start();
        messagingService.processMessage(MESSAGE_CONTENT_CLIENT).setHandler(event -> {
                if(event.succeeded()){
                    async.complete();
                }else{
                    context.fail(event.cause());
                }
            }
        );

        //Then
        verify(client).basicPublish("testQueue", "", MESSAGE_CONTENT_CLIENT);
    }


}
