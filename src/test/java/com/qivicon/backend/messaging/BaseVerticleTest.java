package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.config.Configuration;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpClient;
import io.vertx.core.http.WebSocket;

public class BaseVerticleTest {
    protected static final String MESSAGE_CONTENT = "Hello from client";
    protected static final String HOME_BASE_ID = "Home Base ID";


    protected HttpClient connectWebSocketClient(Vertx vertx, Handler<WebSocket> socketHandler) {
        return vertx.createHttpClient()
                .websocket(Configuration.LISTEN_PORT, "localhost", "/", socketHandler);
    }
}
