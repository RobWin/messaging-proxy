package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.config.Configuration;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BaseTest {
    protected static final JsonObject MESSAGE_CONTENT_CLIENT = new JsonObject("{\"body\":\"Hello from client\"}");
    protected static final JsonObject MESSAGE_CONTENT_SERVER = new JsonObject("{\"body\":\"Hello from server\"}");
    protected static final String HOME_BASE_ID = "Home Base ID";

    protected final Logger LOG = LoggerFactory.getLogger(getClass());

    protected Vertx vertx;

    protected void setUp(TestContext context) {
        LOG.info("Start Vertx");
        vertx = Vertx.vertx();
        // Report uncaught exceptions as Vert.x Unit failures
        vertx.exceptionHandler(context.exceptionHandler());
    }

    public void tearDown(TestContext context) {
        LOG.info("Stop Vertx");
        vertx.close(context.asyncAssertSuccess());
    }


    protected HttpClient connectWebSocketClient(Vertx vertx, Handler<WebSocket> socketHandler) {
        return vertx.createHttpClient()
                .websocket(Configuration.LISTEN_PORT, "localhost", "/", socketHandler,
                        exception -> {
                            LOG.warn("WebSocket connection failed");
                        });
    }
}
