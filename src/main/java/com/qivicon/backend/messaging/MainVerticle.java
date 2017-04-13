package com.qivicon.backend.messaging;

import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

public class MainVerticle extends AbstractVerticle {
    
    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private HttpServer httpServer;

    @Override
    public void start() {
        httpServer = vertx.createHttpServer();
        rxWebSocketServer(httpServer)
            .subscribe(
                this::handleWebSocket,
                failure -> LOG.warn("Failed to create WebSocketServer"),
                () -> LOG.info("WebSocketServer closed")
        );
        httpServer.rxListen(Configuration.LISTEN_PORT).
            subscribe(
                server -> LOG.info("HttpServer is listening on port {}", server.actualPort()),
                exception -> LOG.warn("HttpServer could not start", exception)
            );
    }

    @Override
    public void stop() {
        httpServer.rxClose()
            .subscribe(
                server -> LOG.info("HttpServer is stopped"),
                exception -> LOG.warn("HttpServer could not stop", exception)
        );
    }

    private void handleWebSocket(ServerWebSocket serverWebSocket) {
        LOG.info("Handle ServerWebSocket");
        serverWebSocket.toObservable().subscribe(
                buffer -> {
                    LOG.info("Message received from client: {}", buffer.toString());
                    serverWebSocket.writeTextMessage("Hello world from server");
                },
                exception -> LOG.warn("WebSocket failed"),
                () -> LOG.info("WebSocket closed by client")
        );
    }

    private Observable<ServerWebSocket> rxWebSocketServer(HttpServer server) {
        return server.websocketStream().toObservable();
    }

}
