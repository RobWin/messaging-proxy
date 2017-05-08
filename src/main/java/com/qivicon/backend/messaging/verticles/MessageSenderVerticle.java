package com.qivicon.backend.messaging.verticles;

import com.qivicon.backend.messaging.services.MessagingService;
import com.qivicon.backend.messaging.verticles.events.Events;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSenderVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MessageSenderVerticle.class);
    private MessageConsumer<JsonObject> messageConsumer;
    private final MessagingService messagingService;

    public MessageSenderVerticle(MessagingService messagingService){
        this.messagingService = messagingService;
    }


    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
    }

    @Override
    public void start(Future<Void> startFuture) throws InterruptedException {
        Future<Void> messagingServiceFuture = messagingService.start();
        Future<Void> inboundMessageConsumerFuture = Future.future();

        messageConsumer = vertx.eventBus().consumer(Events.WEBSOCKET_INBOUND_MESSAGE);
        messageConsumer.completionHandler(inboundMessageConsumerFuture.completer());
        messageConsumer.handler(
            (message) -> {
                String clientId = message.headers().get("clientId");
                LOG.debug("Event '{}' consumed: {}", Events.WEBSOCKET_INBOUND_MESSAGE, message.body());
                messagingService.processMessage(clientId, message.body()).setHandler(
                    event -> {
                        if (event.succeeded()) {
                            LOG.info("Inbound message processed from clientId '{}'", clientId);
                        }else{
                            LOG.warn(String.format("Failed to process inbound message from clientId '%s'", clientId), event.cause());
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
