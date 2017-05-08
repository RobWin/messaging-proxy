package com.qivicon.backend.messaging.verticles;


import com.codahale.metrics.SharedMetricRegistries;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.services.MessagingService;
import com.qivicon.backend.messaging.services.impl.DefaultMessagingService;
import io.vertx.config.ConfigRetriever;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Verticle;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static com.qivicon.backend.messaging.ApplicationLauncher.REGISTRY_NAME;

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
        MessagingService messageService = new DefaultMessagingService(AmqpClient.create(vertx,
                SharedMetricRegistries.getOrCreate(REGISTRY_NAME)), config);

        endpointFutures.add(deployVerticle(HttpServerVerticle.class.getName()));
        endpointFutures.add(deployVerticle(new MessageSenderVerticle(messageService)));
        endpointFutures.add(deployVerticle(new MessageListenerVerticle(messageService)));
        return CompositeFuture.all(endpointFutures);
    }

    private Future<Void> deployVerticle(String className) {
        final Future<Void> startFuture = Future.future();
        vertx.deployVerticle(className, stopEvent -> {
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

    private Future<Void> deployVerticle(Verticle verticle) {
        final Future<Void> startFuture = Future.future();
        vertx.deployVerticle(verticle, stopEvent -> {
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
