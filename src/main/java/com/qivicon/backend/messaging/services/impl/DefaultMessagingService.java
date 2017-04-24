package com.qivicon.backend.messaging.services.impl;

import com.qivicon.backend.messaging.client.MessagingClient;
import com.qivicon.backend.messaging.services.MessagingService;
import com.qivicon.backend.messaging.verticles.events.Events;
import com.rabbitmq.client.AMQP;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

import java.util.function.Supplier;

public class DefaultMessagingService implements MessagingService {

    private static final String TO_GATEWAY_EXCHANGE = "to_gateway.direct";
    private static final String TO_BACKEND_EXCHANGE = "to_backend.direct";
    private final Supplier<MessagingClient> messagingClientSupplier;
    private MessagingClient client;

    public DefaultMessagingService(Supplier<MessagingClient> messagingClientSupplier){
        this.messagingClientSupplier = messagingClientSupplier;
    }

    @Override
    public Future<Void> start() {
        client = messagingClientSupplier.get();
        Future<Void> startFuture = Future.future();
        client.connect().setHandler(connectionEvent -> {
                if(connectionEvent.succeeded()){
                    startFuture.complete();
                }{
                    startFuture.fail(connectionEvent.cause());
                }
            }
        );
        return startFuture;
    }

    @Override
    public Future<Void> stop() {
        if(client != null){
            return client.disconnect();
        }else{
            return Future.succeededFuture();
        }
    }

    @Override
    public Future<Void> onClientConnect(String clientId) {
        // 1. Create Exchange, Queue and Binding for client
        // 2. Register message consumer for client

        Future<Void> onClientConnectFuture = Future.future();
        String queueName = clientId;
        String routingKey = clientId;
        Future<String> messageConsumerFuture = createExchangeAndBindToQueue(TO_GATEWAY_EXCHANGE, queueName, routingKey)
                .compose(bindOk -> client.basicConsume(clientId, Events.WEBSOCKET_INBOUND_MESSAGE, true, routingKey));

        messageConsumerFuture.setHandler(event -> {
            if(event.succeeded()){
                onClientConnectFuture.succeeded();
            }else{
                onClientConnectFuture.fail(event.cause());
            }
        });
        return onClientConnectFuture;
    }

    private Future<AMQP.Queue.BindOk> createExchangeAndBindToQueue(String exchangeName, String queueName, String routingKey) {
        AMQP.Exchange.Declare exchangeDeclare = new AMQP.Exchange.Declare.Builder()
                .exchange(exchangeName)
                .build();
        return client.exchangeDeclare(exchangeDeclare)
                .compose(exchangeOK -> {
                    AMQP.Queue.Declare queueDeclare = new AMQP.Queue.Declare.Builder()
                            .queue(queueName)
                            .build();
                    return client.queueDeclare(queueDeclare);
                })
                .compose(queueOK -> client.queueBind(
                        queueName,
                        exchangeName,
                        routingKey));
    }

    @Override
    public Future<Void> onClientDisconnect(String clientId) {
        Future<Void> onClientDisconnectFuture = Future.future();
        // 1. Un-register message consumer of client
        client.basicCancel(clientId).setHandler(onClientDisconnectFuture.completer());
        return onClientDisconnectFuture;
    }

    @Override
    public Future<Void> processMessage(String clientId, JsonObject message) {
        return client.basicPublish(TO_BACKEND_EXCHANGE, clientId, message);
    }
}
