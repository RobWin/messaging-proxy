package com.qivicon.backend.messaging;


import net.jodah.concurrentunit.Waiter;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class IntegrationTest {

    private static final Logger LOG = LoggerFactory.getLogger(IntegrationTest.class);
    private OkHttpClient client;

    @Before
    public void setUp() {
        client = new OkHttpClient.Builder()
                .addNetworkInterceptor(new LoggingInterceptor())
                .readTimeout(0,  TimeUnit.MILLISECONDS)
                .build();
    }

    @After
    public void tearDown() {
        // Trigger shutdown of the dispatcher's executor so this process can exit cleanly.
        client.dispatcher().executorService().shutdown();
    }

    @Test
    public void shouldConnect() throws TimeoutException {
        final Waiter waiter = new Waiter();
        String credentials = Credentials.basic("rabbitmq", "rabbitmq");
        Request request = new Request.Builder()
                .url("ws://localhost:8080")
                .addHeader("Authorization", credentials)
                .build();
        WebSocket ws = client.newWebSocket(request, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                LOG.info("Connected");
                waiter.resume();
            }

            @Override public void onClosing(WebSocket webSocket, int code, String reason) {
                LOG.info("CLOSE: " + code + " " + reason);
            }

            @Override public void onFailure(WebSocket webSocket, Throwable exception, Response response) {
                waiter.rethrow(exception);
            }
        });

        // Wait for resume() to be called
        waiter.await(1000);
    }
}
