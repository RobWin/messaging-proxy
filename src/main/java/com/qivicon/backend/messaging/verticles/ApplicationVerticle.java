package com.qivicon.backend.messaging.verticles;


import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ApplicationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        List<Future> endpointFutures = new ArrayList<>();
        endpointFutures.add(deployVerticle(HttpServerVerticle.class.getName()));
        endpointFutures.add(deployVerticle(MessageSenderVerticle.class.getName()));
        endpointFutures.add(deployVerticle(MessageListenerVerticle.class.getName()));
        CompositeFuture.all(endpointFutures).setHandler(startup -> {
                if (startup.succeeded()) {
                    startFuture.complete();
                } else {
                    startFuture.fail(startup.cause());
                }
            }
        );
    }

    private Future<Void> deployVerticle(String className) {
        final Future<Void> startFuture = Future.future();
        vertx.deployVerticle(className, event -> {
            if(event.succeeded()){
                LOG.info("Deployed Verticle: {}", className);
                startFuture.complete();
            }else{
                LOG.error("Failed to deploy Verticle: {}", className, event.cause());
                startFuture.fail(event.cause());
            }

        });
        return startFuture;
    }
}
