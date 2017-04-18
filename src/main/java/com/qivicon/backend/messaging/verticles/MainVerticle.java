package com.qivicon.backend.messaging.verticles;

import com.codahale.metrics.SharedMetricRegistries;
import com.qivicon.backend.messaging.config.Configuration;
import com.qivicon.backend.messaging.metrics.MetricsHandler;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.http.HttpServer;
import io.vertx.rxjava.core.http.ServerWebSocket;
import io.vertx.rxjava.ext.healthchecks.HealthCheckHandler;
import io.vertx.rxjava.ext.web.Router;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import java.util.ArrayList;
import java.util.List;

import static com.qivicon.backend.messaging.ApplicationLauncher.REGISTRY_NAME;

public class MainVerticle extends AbstractVerticle {
    
    private static final Logger LOG = LoggerFactory.getLogger(MainVerticle.class);

    private HttpServer httpServer;

    @Override
    public void start(Future<Void> startFuture) {
        List<Future> endpointFutures = new ArrayList<>();
        endpointFutures.add(startHttpServer());
        //endpointFutures.add(deployVerticle(MessageListenerVerticle.class.getName()));
        CompositeFuture.all(endpointFutures).setHandler(startup -> {
                if (startup.succeeded()) {
                    startFuture.complete();
                } else {
                    startFuture.fail(startup.cause());
                }
            }
        );
    }

    @Override
    public void stop(Future<Void> stopFuture) {
        httpServer.rxClose()
            .subscribe(
                server -> {
                    LOG.info("HttpServer is stopped");
                    stopFuture.complete();
                },
                exception -> {
                    LOG.warn("HttpServer could not stop", exception);
                    stopFuture.fail(exception);
                }
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

    private Future<Void> startHttpServer(){
        final Future<Void> startFuture = Future.future();
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
        //healthCheckHandler.register("healthCheck", future -> future.complete(Status.OK()));

        Router router = Router.router(vertx);
        router.get("/health*").handler(healthCheckHandler);
        router.get("/prometheus*").handler(new MetricsHandler(SharedMetricRegistries.getOrCreate(REGISTRY_NAME)));

        httpServer = vertx.createHttpServer().
                requestHandler(router::accept);
        rxWebSocketServer(httpServer)
                .subscribe(
                        this::handleWebSocket,
                        exception -> LOG.warn("Failed to create WebSocketServer"),
                        () -> LOG.info("WebSocketServer closed")
                );
        httpServer.rxListen(Configuration.LISTEN_PORT).
                subscribe(
                        server -> {
                            LOG.info("HttpServer is listening on port {}", server.actualPort());
                            startFuture.complete();
                        },
                        exception -> {
                            LOG.warn("HttpServer could not start", exception);
                            startFuture.fail(exception);
                        }
                );
        return startFuture;
    }

    private Future<Void> deployVerticle(String className) {
        final Future<Void> startFuture = Future.future();
        vertx.rxDeployVerticle(className)
                .subscribe(
                        verticleId -> {
                            LOG.info("Deployed Verticle: {}", className);
                            startFuture.complete();
                        },
                        exception -> {
                            LOG.warn("Failed to deploy Verticle: {}", className, exception);
                            startFuture.fail(exception);
                        }
                );
        return startFuture;
    }

}
