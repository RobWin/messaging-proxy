package com.qivicon.backend.messaging.services;

import com.qivicon.backend.messaging.BaseTest;
import com.qivicon.backend.messaging.client.AmqpChannel;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.client.AmqpConnection;
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
    private AmqpClient client;
    private AmqpConnection connection;
    private AmqpChannel channel;


    @Before
    public void setUp(TestContext context) {
        super.setUp(context);
        client = mock(AmqpClient.class);
        connection = mock(AmqpConnection.class);
        channel = mock(AmqpChannel.class);
        when(client.connect(null, -1, null, null)).thenReturn(Future.succeededFuture(connection));
        when(connection.createChannel()).thenReturn(Future.succeededFuture(channel));
        when(connection.close()).thenReturn(Future.succeededFuture());
        messagingService = new DefaultMessagingService(client);
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
        when(channel.basicPublish(anyString(), anyString(), anyObject())).thenReturn(Future.succeededFuture());

        //When
        Async async = context.async();
        messagingService.start();
        messagingService.processMessage("clientId", MESSAGE_CONTENT_CLIENT).setHandler(event -> {
                if(event.succeeded()){
                    async.complete();
                }else{
                    context.fail(event.cause());
                }
            }
        );

        //Then
        verify(channel).basicPublish("to_backend.direct", "clientId", MESSAGE_CONTENT_CLIENT);
    }


}
