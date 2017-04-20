package com.qivicon.backend.messaging.verticles;

import com.qivicon.backend.messaging.events.Events;
import com.qivicon.backend.messaging.rabbitmq.MessagingService;
import com.qivicon.backend.messaging.rabbitmq.RabbitMQService;
import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageListenerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MessageListenerVerticle.class);
    private MessageConsumer<String> connectionOpenedEventConsumer;
    private MessageConsumer<String> connectionClosedEventConsumer;
    private final MessagingService messagingService;

    public MessageListenerVerticle(){
        this.messagingService = new RabbitMQService();
    }

    public MessageListenerVerticle(MessagingService messagingService){
        this.messagingService = messagingService;
    }

    @Override
    public void start(Future<Void> startFuture) throws InterruptedException {
        //messagingService.start(startFuture);
        connectionOpenedEventConsumer = vertx.eventBus().consumer(Events.WEBSOCKET_CONNECTION_OPENED);
        connectionOpenedEventConsumer.completionHandler(event -> {
            if(event.succeeded()) {
                LOG.info("Connection opened event consumer registration has reached all nodes");
            }else{
                LOG.warn("Connection opened event consumer registration has failed!");
                startFuture.fail(event.cause());
            }
        });
        connectionOpenedEventConsumer.handler(connectionOpenedEvent -> {
            LOG.debug("Event '{}' consumed with body: {}", Events.WEBSOCKET_CONNECTION_OPENED, connectionOpenedEvent.body());
            messagingService.onClientConnect(connectionOpenedEvent.body());
        });
        connectionClosedEventConsumer = vertx.eventBus().consumer(Events.WEBSOCKET_CONNECTION_CLOSED);
        connectionClosedEventConsumer.completionHandler(event -> {
            if(event.succeeded()) {
                LOG.info("Connection closed event consumer registration has reached all nodes");
            }else{
                LOG.warn("Connection closed event consumer registration has failed!");
                startFuture.fail(event.cause());
            }
        });
        connectionClosedEventConsumer.handler((connectionClosedEvent) -> {
            LOG.debug("Event '{}' consumed with body: {}", Events.WEBSOCKET_CONNECTION_CLOSED, connectionClosedEvent.body());
            messagingService.onClientDisconnect(connectionClosedEvent.body());
        });
        LOG.info("Started Verticle: {}", this.getClass().getName());
        startFuture.complete();
    }


    @Override
    public void stop(Future<Void> stopFuture) throws InterruptedException {
        //messagingService.stop(stopFuture);
        connectionOpenedEventConsumer.unregister(event -> {
            if(event.succeeded()){
                LOG.info("Connection opened event consumer un-registration has reached all nodes");
            }else{
                LOG.info("Inbound message event consumer un-registration has failed", event.cause());
                stopFuture.fail(event.cause());
            }
        });
        connectionClosedEventConsumer.unregister(event -> {
            if(event.succeeded()){
                LOG.info("Connection closed event consumer un-registration has reached all nodes");
            }else{
                LOG.info("Inbound message event consumer un-registration has failed", event.cause());
                stopFuture.fail(event.cause());
            }
        });
        LOG.info("Stopped Verticle: {}", this.getClass().getName());
        stopFuture.complete();

    }
}