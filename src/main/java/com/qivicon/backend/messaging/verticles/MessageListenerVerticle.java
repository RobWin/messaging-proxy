package com.qivicon.backend.messaging.verticles;

import io.vertx.core.Future;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.rabbitmq.RabbitMQClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.rabbitmq.RabbitMQClientFactory.RABBITMQ_CLIENT_FACTORY_INSTANCE;

public class MessageListenerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MessageListenerVerticle.class);
    private RabbitMQClient client;

    @Override
    public void start(Future<Void> startFuture) throws InterruptedException {
        RabbitMQClient client = RABBITMQ_CLIENT_FACTORY_INSTANCE.getRabbitClient(vertx);
        client.rxStart()
            .subscribe(
                    (success) -> {
                        LOG.info("Started Verticle: {}", this.getClass().getName());
                        startFuture.complete();
                    },
                    exception -> {
                        LOG.error("Failed to start Verticle: {}", this.getClass().getName(), exception);
                        startFuture.fail(exception);
                    }
            );
    }


    @Override
    public void stop(Future<Void> stopFuture) throws InterruptedException {
        client.rxStop()
            .subscribe(
                    (success) -> {
                        LOG.info("Stopped Verticle: {}", this.getClass().getName());
                        stopFuture.complete();
                    },
                    exception -> {
                        LOG.error("Failed to stop Verticle: {}", this.getClass().getName(), exception);
                        stopFuture.fail(exception);
                    }
            );
    }
}