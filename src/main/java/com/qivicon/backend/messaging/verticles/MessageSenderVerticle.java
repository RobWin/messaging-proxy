package com.qivicon.backend.messaging.verticles;

import com.qivicon.backend.messaging.verticles.events.Events;
import com.qivicon.backend.messaging.services.MessagingService;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

public class MessageSenderVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MessageSenderVerticle.class);
    private MessageConsumer<JsonObject> messageConsumer;
    private final Supplier<MessagingService> messagingServiceFactory;
    private MessagingService messagingService;

    public MessageSenderVerticle(Supplier<MessagingService> messagingServiceFactory){
        this.messagingServiceFactory = messagingServiceFactory;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        messagingService = messagingServiceFactory.get();
    }

    @Override
    public void start(Future<Void> startFuture) throws InterruptedException {
        Future<Void> inboundMessageConsumerFuture = Future.future();
        Future<Void> messagingServiceFuture = messagingService.start();

        messageConsumer = vertx.eventBus().consumer(Events.WEBSOCKET_INBOUND_MESSAGE);
        messageConsumer.completionHandler(inboundMessageConsumerFuture.completer());
        messageConsumer.handler(
            (message) -> {
                LOG.info("Event '{}' consumed: {}", Events.WEBSOCKET_INBOUND_MESSAGE, message.body());
                messagingService.processMessage(message.body()).setHandler(
                    event -> {
                        if (event.succeeded()) {
                            LOG.info("Inbound message processed");
                        }else{
                            LOG.info("Failed to process inbound message", event.cause());
                        }
                    }
                );
            }
        );

        CompositeFuture.all(messagingServiceFuture, inboundMessageConsumerFuture)
            .setHandler(startEvent -> {
                if (startEvent.succeeded()) {
                    // All consumers unregistered
                    LOG.info("Started Verticle: {}", this.getClass().getName());
                    startFuture.complete();
                } else {
                    // At least one server failed
                    startFuture.fail(startEvent.cause());
                }
            });
    }


    @Override
    public void stop(Future<Void> stopFuture) throws InterruptedException {
        Future<Void> inboundMessageConsumerFuture = Future.future();
        Future<Void> messagingServiceFuture = messagingService.stop();

        messageConsumer.unregister(inboundMessageConsumerFuture.completer());

        CompositeFuture.all(messagingServiceFuture, inboundMessageConsumerFuture)
            .setHandler(closeEvent -> {
                if (closeEvent.succeeded()) {
                    // All consumers unregistered
                    LOG.info("Stopped Verticle: {}", this.getClass().getName());
                    stopFuture.complete();
                } else {
                    // At least one server failed
                    stopFuture.fail(closeEvent.cause());
                }
            });
    }
}
