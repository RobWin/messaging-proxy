package com.qivicon.backend.messaging.verticles;

import com.codahale.metrics.SharedMetricRegistries;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.services.MessagingService;
import com.qivicon.backend.messaging.services.impl.DefaultMessagingService;
import com.qivicon.backend.messaging.verticles.events.Events;
import io.vertx.core.*;
import io.vertx.core.eventbus.MessageConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.ApplicationLauncher.REGISTRY_NAME;

public class MessageListenerVerticle extends AbstractVerticle {

    private static final Logger LOG = LoggerFactory.getLogger(MessageListenerVerticle.class);
    private MessageConsumer<String> connectionOpenedEventConsumer;
    private MessageConsumer<String> connectionClosedEventConsumer;
    private MessagingService messagingService;

    public MessageListenerVerticle ()  {}

    public MessageListenerVerticle(MessagingService messagingService){
        this.messagingService = messagingService;
    }

    @Override
    public void init(Vertx vertx, Context context) {
        super.init(vertx, context);
        if(messagingService == null){
           this.messagingService = new DefaultMessagingService(AmqpClient.create(vertx,
                    SharedMetricRegistries.getOrCreate(REGISTRY_NAME)), config());
        }
    }

    @Override
    public void start(Future<Void> startFuture) throws InterruptedException {
        Future<Void> connectionOpenedEventConsumerFuture = Future.future();
        Future<Void> connectionClosedEventConsumerFuture = Future.future();
        Future<Void> messagingServiceFuture = messagingService.start();

        connectionOpenedEventConsumer = vertx.eventBus().localConsumer(Events.WEBSOCKET_CONNECTION_OPENED);
        connectionOpenedEventConsumer.completionHandler(connectionOpenedEventConsumerFuture.completer());
        connectionOpenedEventConsumer.handler(connectionOpenedEvent -> {
            LOG.info("Event '{}' consumed for client: {}", Events.WEBSOCKET_CONNECTION_OPENED, connectionOpenedEvent.body());
            messagingService.onClientConnect(connectionOpenedEvent.body());
        });

        connectionClosedEventConsumer = vertx.eventBus().localConsumer(Events.WEBSOCKET_CONNECTION_CLOSED);
        connectionClosedEventConsumer.completionHandler(connectionClosedEventConsumerFuture.completer());
        connectionClosedEventConsumer.handler((connectionClosedEvent) -> {
            LOG.info("Event '{}' consumed for client: {}", Events.WEBSOCKET_CONNECTION_CLOSED, connectionClosedEvent.body());
            messagingService.onClientDisconnect(connectionClosedEvent.body());
        });

        CompositeFuture.all(messagingServiceFuture, connectionOpenedEventConsumerFuture, connectionClosedEventConsumerFuture)
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
        Future<Void> connectionOpenedEventConsumerFuture = Future.future();
        Future<Void> connectionClosedEventConsumerFuture = Future.future();
        Future<Void> messagingServiceFuture = messagingService.stop();

        connectionOpenedEventConsumer.unregister(connectionOpenedEventConsumerFuture.completer());
        connectionClosedEventConsumer.unregister(connectionClosedEventConsumerFuture.completer());

        CompositeFuture.all(messagingServiceFuture, connectionOpenedEventConsumerFuture, connectionClosedEventConsumerFuture)
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