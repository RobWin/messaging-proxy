package com.qivicon.backend.messaging.verticles;

import com.codahale.metrics.SharedMetricRegistries;
import com.qivicon.backend.messaging.verticles.auth.QbertAuthProvider;
import com.qivicon.backend.messaging.verticles.metrics.PrometheusMetricsHandler;
import com.qivicon.backend.messaging.verticles.websocket.WebSocketHandler;
import io.vertx.core.*;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.healthchecks.HealthCheckHandler;
import io.vertx.ext.healthchecks.Status;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.AuthHandler;
import io.vertx.ext.web.handler.BasicAuthHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.ApplicationLauncher.REGISTRY_NAME;

public class HttpServerVerticle extends AbstractVerticle {
    
    private static final Logger LOG = LoggerFactory.getLogger(HttpServerVerticle.class);

    private HttpServer httpServer;
    private Handler<ServerWebSocket> websocketHandler;
    private int serverPort;

    public HttpServerVerticle ()  {}

    public HttpServerVerticle (Handler<ServerWebSocket> websocketHandler)  {
        this.websocketHandler = websocketHandler;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        if(websocketHandler == null){
            this.websocketHandler = new WebSocketHandler(this.vertx.eventBus());
        }
        serverPort = config().getInteger("server.port", 8080);
    }


    @Override
    public void start(Future<Void> startFuture) {
        HealthCheckHandler healthCheckHandler = HealthCheckHandler.create(vertx);
        healthCheckHandler.register("healthCheck", future -> future.complete(Status.OK()));

        AuthProvider authProvider = new QbertAuthProvider();
        AuthHandler basicAuthHandler = BasicAuthHandler.create(authProvider, "messaging");

        Router router = Router.router(vertx);
        router.route("/messaging").handler(basicAuthHandler);
        router.route("/messaging").handler(requestContext -> {
            String clientId = requestContext.user().principal().getString("username");
            ServerWebSocket webSocket = requestContext.request().upgrade();
            webSocket.headers().add("clientId", clientId);
            websocketHandler.handle(webSocket);
        });
        router.get("/health*").handler(healthCheckHandler);
        router.get("/prometheus*").handler(new PrometheusMetricsHandler(SharedMetricRegistries.getOrCreate(REGISTRY_NAME)));

        httpServer = vertx.createHttpServer().
                requestHandler(router::accept);
        //httpServer.connectionHandler(connection -> LOG.debug("HTTP Connection opened"));

        httpServer.listen(serverPort, event -> {
                if (event.succeeded()) {
                    LOG.info("Started Verticle: {}", this.getClass().getName());
                    LOG.info("HttpServer is listening on port {}", event.result().actualPort());
                    startFuture.complete();
                } else {
                    Throwable exception = event.cause();
                    LOG.error("Failed to connect HttpServer", exception);
                    startFuture.fail(exception);
                }
            }
        );
    }

    @Override
    public void stop(Future<Void> stopFuture) {
        httpServer.close(event -> {
                LOG.info("Stopped Verticle: {}", this.getClass().getName());
                LOG.info("HttpServer is stopped");
                stopFuture.complete();
            }
        );
    }

}
