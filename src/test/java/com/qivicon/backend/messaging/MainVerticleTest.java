package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.config.Configuration;
import com.qivicon.backend.messaging.verticles.MainVerticle;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.core.http.HttpClient;
import io.vertx.rxjava.core.http.WebSocket;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

    private static final Logger LOG = LoggerFactory.getLogger(MainVerticleTest.class);

    private Vertx vertx;

    @Before
    public void setUp(TestContext context) {
        LOG.info("Start Vertx");
        vertx = Vertx.vertx();
        vertx.deployVerticle(MainVerticle.class.getName(),
                context.asyncAssertSuccess());
        
    }

    @After
    public void tearDown(TestContext context) {
        LOG.info("Stop Vertx");
        vertx.close(context.asyncAssertSuccess());
    }

    @Test(timeout = 3000)
    public void shouldConnectToWebSocketServer(TestContext context) {
        final Async async = context.async();
        HttpClient client = createHttpClient();
        connect(client)
            .subscribe(
                socket -> handleSocket(context, async, client, socket),
                exception -> {
                    LOG.info("WebSocket connection failed");
                    context.fail(exception);
                },
                () -> LOG.info("WebSocket client connected")
            );
    }

    private void handleSocket(TestContext context, Async async, HttpClient client, WebSocket socket) {
        socket.writeTextMessage("Hello from client").toObservable()
            .subscribe(
                buffer -> {
                    LOG.warn("Message received from server: {}", buffer.toString());
                    context.assertEquals("Hello world from server", buffer.toString());
                    client.close();
                },
                exception -> {
                    LOG.warn("WebSocket failed");
                    context.fail(exception);
                },
                () -> {
                    LOG.info("WebSocket closed");
                    LOG.info("Async completed");
                    LOG.info("Async count {}", async.count());
                    async.complete();
                    LOG.info("Async count {}", async.count());
                }
            );
    }

    private HttpClient createHttpClient() {
        return vertx.createHttpClient();
    }

    private Observable<WebSocket> connect(HttpClient client) {
        return client
                .websocketStream(Configuration.LISTEN_PORT, "localhost", "/")
                .toObservable();
    }
}
