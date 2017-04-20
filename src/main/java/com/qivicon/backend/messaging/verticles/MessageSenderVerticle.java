package com.qivicon.backend.messaging.verticles;

import com.qivicon.backend.messaging.events.Events;
import com.qivicon.backend.messaging.rabbitmq.MessagingService;
import com.qivicon.backend.messaging.rabbitmq.RabbitMQService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageSenderVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MessageSenderVerticle.class);
    private MessageConsumer<String> messageConsumer;
    private final MessagingService messagingService;

    public MessageSenderVerticle(){
        this.messagingService = new RabbitMQService();

    }

    public MessageSenderVerticle(MessagingService messagingService){
        this.messagingService = messagingService;
    }

    @Override
    public void start(Future<Void> startFuture) throws InterruptedException {
        //messagingService.start(startFuture);
        messageConsumer = vertx.eventBus().consumer(Events.WEBSOCKET_INBOUND_MESSAGE);
        messageConsumer.completionHandler(event -> {
           if(event.succeeded()) {
               LOG.info("Inbound message event consumer registration has reached all nodes");
               LOG.info("Started Verticle: {}", this.getClass().getName());
               startFuture.complete();

           }else{
               LOG.warn("Inbound message event consumer registration has failed!");
               startFuture.fail(event.cause());
           }
        });
        messageConsumer.handler(
                (message) -> {
                    LOG.info("Event '{}' consumed: {}", Events.WEBSOCKET_INBOUND_MESSAGE, message.body());
                    messagingService.processMessage(message.body());
                }
        );
    }


    @Override
    public void stop(Future<Void> stopFuture) throws InterruptedException {
        messageConsumer.unregister(event -> {
            if(event.succeeded()){
                LOG.info("Inbound message event consumer un-registration has reached all nodes");
                LOG.info("Stopped Verticle: {}", this.getClass().getName());
                stopFuture.complete();
            }else{
                LOG.info("Inbound message event consumer un-registration has failed", event.cause());
                stopFuture.fail(event.cause());
            }
        });
        //messagingService.stop(stopFuture);
    }
}
