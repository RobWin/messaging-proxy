package com.qivicon.backend.messaging.verticles;


import io.vertx.config.ConfigRetriever;
import io.vertx.core.*;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class ApplicationVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationVerticle.class);

    @Override
    public void start(Future<Void> startFuture) {
        /*
        ConfigStoreOptions envStore = new ConfigStoreOptions()
                .setType("env");
        ConfigRetrieverOptions options = new ConfigRetrieverOptions()
                .addStore(envStore);
                */
        ConfigRetriever.create(vertx)
            .getConfig(loadConfigEvent -> {
                if (loadConfigEvent.succeeded()) {
                    JsonObject config = loadConfigEvent.result();
                    deployVerticles(config)
                        .setHandler(startEvent -> {
                            if (startEvent.succeeded()) {
                                startFuture.complete();
                            } else {
                                startFuture.fail(startEvent.cause());
                            }
                        });
                } else {
                    startFuture.fail(loadConfigEvent.cause());
                }
            });
    }

    private CompositeFuture deployVerticles(JsonObject config) {
        List<Future> endpointFutures = new ArrayList<>();
        DeploymentOptions deploymentOptions = new DeploymentOptions().setConfig(config).setInstances(3);

        endpointFutures.add(deployVerticle(HttpServerVerticle.class.getName(), deploymentOptions));
        endpointFutures.add(deployVerticle(MessageListenerVerticle.class.getName(), deploymentOptions));
        endpointFutures.add(deployVerticle(MessageSenderVerticle.class.getName(), deploymentOptions));
        return CompositeFuture.all(endpointFutures);
    }

    private Future<Void> deployVerticle(String className, DeploymentOptions deploymentOptions) {
        final Future<Void> startFuture = Future.future();
        vertx.deployVerticle(className, deploymentOptions, stopEvent -> {
            if(stopEvent.succeeded()){
                LOG.info("Deployed Verticle: {}", className);
                startFuture.complete();
            }else{
                LOG.error("Failed to deploy Verticle: {}", className, stopEvent.cause());
                startFuture.fail(stopEvent.cause());
            }

        });
        return startFuture;
    }

    private Future<Void> deployVerticle(Verticle verticle, DeploymentOptions deploymentOptions) {
        final Future<Void> startFuture = Future.future();
        vertx.deployVerticle(verticle, deploymentOptions, stopEvent -> {
            if(stopEvent.succeeded()){
                LOG.info("Deployed Verticle: {}", verticle.getClass().getName());
                startFuture.complete();
            }else{
                LOG.error("Failed to deploy Verticle: {}", verticle.getClass().getName(), stopEvent.cause());
                startFuture.fail(stopEvent.cause());
            }
        });
        return startFuture;
    }
}
