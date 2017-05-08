package com.qivicon.backend.messaging.services.impl;

import com.qivicon.backend.messaging.client.AmqpChannel;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.client.AmqpConnection;
import com.qivicon.backend.messaging.services.MessagingService;
import com.qivicon.backend.messaging.verticles.events.Events;
import com.rabbitmq.client.AMQP;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultMessagingService implements MessagingService {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultMessagingService.class);

    public static final String TO_GATEWAY_EXCHANGE = "to_gateway.direct";
    public static final String TO_BACKEND_EXCHANGE = "to_backend.direct";
    public static final String CONFIG_PREXIX = "RABBITMQ_";
    private AmqpClient amqpClient;
    private JsonObject config;
    private AmqpConnection connection;
    private AmqpChannel channel;

    public DefaultMessagingService(AmqpClient amqpClient){
        this.amqpClient = amqpClient;
        this.config = new JsonObject();
    }

    public DefaultMessagingService(AmqpClient amqpClient, JsonObject config){
        this.amqpClient = amqpClient;
        this.config = config;
    }

    @Override
    public Future<Void> start() {
        String host = config.getString(CONFIG_PREXIX + "HOST");
        Integer port = config.getInteger(CONFIG_PREXIX + "PORT", -1);
        String user = config.getString(CONFIG_PREXIX + "USER");
        String password = config.getString(CONFIG_PREXIX + "PASSWORD");
        Future<Void> startFuture = Future.future();

        amqpClient.connect(host, port, user, password)
            .compose(connection -> {
                this.connection = connection;
                return connection.createChannel();
            })
            .setHandler(connectionEvent -> {
                if(connectionEvent.succeeded()){
                    LOG.info("Started Service: {}", this.getClass().getName());
                    channel = connectionEvent.result();
                    startFuture.complete();
                }else{
                    startFuture.fail(connectionEvent.cause());
                }
            }
        );
        return startFuture;
    }

    @Override
    public Future<Void> stop() {
        if(connection != null){
            return connection.close();
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
        Future<AmqpChannel> messageConsumerFuture = createExchangeAndBindToQueue(TO_GATEWAY_EXCHANGE, queueName, routingKey)
                .compose(channel -> channel.basicConsume(clientId, Events.createOutboundMessageAddress(clientId), routingKey));

        messageConsumerFuture.setHandler(event -> {
            if(event.succeeded()){
                LOG.info("Created exchange '{}', queue '{}' and consumer for clientId '{}'", TO_GATEWAY_EXCHANGE, queueName, clientId);
                onClientConnectFuture.complete();
            }else{
                onClientConnectFuture.fail(event.cause());
            }
        });
        return onClientConnectFuture;
    }

    private Future<AmqpChannel> createExchangeAndBindToQueue(String exchangeName, String queueName, String routingKey) {
        return createExchange(channel, exchangeName)
                .compose(channel -> createQueue(channel, queueName))
                .compose(channel -> bindQueue(channel, exchangeName, queueName, routingKey));
    }

    public Future<AmqpChannel> createExchange(AmqpChannel channel, String exchangeName) {
        AMQP.Exchange.Declare exchangeDeclare = new AMQP.Exchange.Declare.Builder()
                .exchange(exchangeName)
                .build();
        return channel.exchangeDeclare(exchangeDeclare);
    }

    public Future<AmqpChannel> createQueue(AmqpChannel channel, String queueName) {
        AMQP.Queue.Declare queueDeclare = new AMQP.Queue.Declare.Builder()
                .queue(queueName)
                .build();
        return channel.queueDeclare(queueDeclare);
    }

    public Future<AmqpChannel> bindQueue(AmqpChannel channel, String exchangeName, String queueName, String routingKey) {
        return channel.queueBind(
                queueName,
                exchangeName,
                routingKey);
    }

    @Override
    public Future<Void> onClientDisconnect(String clientId) {
        Future<Void> onClientDisconnectFuture = Future.future();
        // 1. Un-register message consumer of client
        channel.basicCancel(clientId)
            .setHandler(event -> {
                if (event.succeeded()) {
                    onClientDisconnectFuture.complete();
                } else {
                    onClientDisconnectFuture.fail(event.cause());
                }
            });
        return onClientDisconnectFuture;
    }

    @Override
    public Future<Void> processMessage(String clientId, JsonObject message) {
        Future<Void> processMessageFuture = Future.future();
        //TODO Use reply to queue
        channel.basicPublish(TO_BACKEND_EXCHANGE, clientId, message)
            .setHandler(event -> {
                if(event.succeeded()){
                    LOG.info("Published message to exchange '{}' and routingKey '{}'", TO_BACKEND_EXCHANGE, clientId);
                    processMessageFuture.complete();
                }else{
                    LOG.warn("Failed to publish message to exchange '{}' and routingKey '{}'", TO_BACKEND_EXCHANGE, clientId);
                    processMessageFuture.fail(event.cause());
                }
            });
        return processMessageFuture;
    }
}
