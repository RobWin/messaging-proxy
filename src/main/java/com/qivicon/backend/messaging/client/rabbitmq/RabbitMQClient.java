package com.qivicon.backend.messaging.client.rabbitmq;

import com.qivicon.backend.messaging.client.MessagingClient;
import com.qivicon.backend.messaging.client.rabbitmq.consumer.EventBusForwarder;
import com.rabbitmq.client.*;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static com.qivicon.backend.messaging.client.rabbitmq.Utils.APPLICATION_JSON;
import static com.qivicon.backend.messaging.client.rabbitmq.Utils.encode;
import static com.qivicon.backend.messaging.client.rabbitmq.Utils.fromJson;


public class RabbitMQClient implements MessagingClient, ShutdownListener, AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQClient.class);

    private final Vertx vertx;
    private final JsonObject config;
    private final Integer retries;

    private Connection connection;
    private Channel channel;
    public static final String CONFIG_PREXIX = "rabbitmq.";
    private final ConnectionFactory connectionFactory;

    static RabbitMQClient create(Vertx vertx, JsonObject config){
        return new RabbitMQClient(vertx, config);
    }

    private RabbitMQClient(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
        this.retries = config.getInteger(CONFIG_PREXIX + "connectionRetries", 3);
        this.connectionFactory = new ConnectionFactory();
    }

    private void newConnection() throws IOException, TimeoutException {
        connection = newConnection(config);
        connection.addShutdownListener(this);
        log.info("Connected to RabbitMQ");
        channel = connection.createChannel();
        log.info("Created channel {} to RabbitMQ", channel.getChannelNumber());
    }

    private Connection newConnection(JsonObject config) throws IOException, TimeoutException {
        String uri = config.getString("uri");
        // Use uri if set, otherwise support individual connection parameters
        if (uri != null) {
            try {
                connectionFactory.setUri(uri);
            } catch (Exception e) {
                throw new IllegalArgumentException("Invalid rabbitmq connection uri " + uri);
            }
        } else {
            String user = config.getString(CONFIG_PREXIX + "user");
            if (user != null) {
                connectionFactory.setUsername(user);
            }
            String password = config.getString(CONFIG_PREXIX + "password");
            if (password != null) {
                connectionFactory.setPassword(password);
            }
            String host = config.getString(CONFIG_PREXIX + "host");
            if (host != null) {
                connectionFactory.setHost(host);
            }
            Integer port = config.getInteger(CONFIG_PREXIX + "port");
            if (port != null) {
                connectionFactory.setPort(port);
            }

            String virtualHost = config.getString(CONFIG_PREXIX + "virtualHost");
            if (virtualHost != null) {
                connectionFactory.setVirtualHost(virtualHost);
            }
        }

        // Connection timeout
        Integer connectionTimeout = config.getInteger(CONFIG_PREXIX + "connectionTimeout");
        if (connectionTimeout != null) {
            connectionFactory.setConnectionTimeout(connectionTimeout);
        }

        Integer requestedHeartbeat = config.getInteger(CONFIG_PREXIX + "requestedHeartbeat");
        if (requestedHeartbeat != null) {
            connectionFactory.setRequestedHeartbeat(requestedHeartbeat);
        }

        Integer handshakeTimeout = config.getInteger(CONFIG_PREXIX + "handshakeTimeout");
        if (handshakeTimeout != null) {
            connectionFactory.setHandshakeTimeout(handshakeTimeout);
        }


        Integer requestedChannelMax = config.getInteger(CONFIG_PREXIX + "requestedChannelMax");
        if (requestedChannelMax != null) {
            connectionFactory.setRequestedChannelMax(requestedChannelMax);
        }

        Integer networkRecoveryInterval = config.getInteger(CONFIG_PREXIX + "networkRecoveryInterval");
        if (networkRecoveryInterval != null) {
            connectionFactory.setNetworkRecoveryInterval(networkRecoveryInterval);
        }

        // Automatic recovery of connections/channels/etc.
        boolean automaticRecoveryEnabled = config.getBoolean(CONFIG_PREXIX + "automaticRecoveryEnabled", true);
        connectionFactory.setAutomaticRecoveryEnabled(automaticRecoveryEnabled);

        return connectionFactory.newConnection();
    }

    @Override
    public Future<Connection> connect() {
        log.info("Start RabbitMQClient");
        Future<Connection> startFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                newConnection();
                future.complete();
            } catch (IOException | TimeoutException e) {
                log.error(String.format("Could not connect to RabbitMQ [%s:%d]",
                        connectionFactory.getHost(),
                        connectionFactory.getPort()), e);
                if (retries > 0) {
                    try {
                        reconnect(future);
                    } catch (IOException ioex) {
                        future.fail(ioex);
                    }
                } else {
                    future.fail(e);
                }
            }
        }, startFuture);
        return startFuture;
    }

    private void reconnect(Future<Connection> future) throws IOException {
        log.info("Attempting to reconnect to RabbitMQ [{}:{}]",
                connectionFactory.getHost(),
                connectionFactory.getPort());
        AtomicInteger attempts = new AtomicInteger(0);
        int retries = this.retries;
        long delay = config.getLong("connectionRetryDelay", 10000L); // Every 10 seconds by default
        vertx.setPeriodic(delay, id -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == retries) {
                vertx.cancelTimer(id);
                log.info("Max number of connection attempts (" + retries + ") reached. Will not attempt to connect again");
                future.fail("Max number of connect attempts (" + retries + ") reached. Will not attempt to connect again");
            } else {
                try {
                    log.debug("Reconnect attempt # " + attempt);
                    newConnection();
                    vertx.cancelTimer(id);
                    log.info("Successfully reconnected to RabbitMQ [{}:{}] (attempt # " + attempt + ")",
                            connectionFactory.getHost(),
                            connectionFactory.getPort());
                    future.complete(connection);
                } catch (IOException | TimeoutException e) {
                    log.debug("Failed to connection attempt # " + attempt, e);
                }
            }
        });
    }

    @Override
    public Future<Void> disconnect() {
        log.info("Stop RabbitMQClient");
        Future<Void> disconnectFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                doDisconnect();
                future.complete();
            } catch (IOException e) {
                future.fail(e);
            }
        }, disconnectFuture);
        return disconnectFuture;
    }

    @Override
    public Future<String> basicConsume(String queueName, String eventBusAddress) {
        return withChannel(channel -> channel.basicConsume(queueName,
                new EventBusForwarder(vertx, channel, eventBusAddress)));
    }

    @Override
    public Future<Void> basicCancel(String consumerTag) {
        return withVoidChannel(channel -> channel.basicCancel(consumerTag));
    }

    private void doDisconnect() throws IOException {
        try {
            // This will close all channels related to this connection
            connection.close();
            log.debug("Disconnected from RabbitMQ");
        } finally {
            connection = null;
            channel = null;
        }
    }

    @Override
    public Future<String> basicConsume(String queueName, String eventBusAddress, boolean autoAck, String consumerTag) {
        return withChannel(channel -> channel.basicConsume(queueName, autoAck, consumerTag, new EventBusForwarder(vertx, channel, eventBusAddress)));
    }

    @Override
    public Future<Void> basicPublish(String exchangeName, String routingKey, JsonObject message) {
        return withVoidChannel(channel -> {
            JsonObject properties = message.getJsonObject("properties");
            String contentType = properties == null ? null : properties.getString("contentType");
            String encoding = properties == null ? "UTF-8" : properties.getString("contentEncoding");
            byte[] body;
            if (contentType != null) {
                switch (contentType) {
                    case APPLICATION_JSON:
                        body = encode(encoding, message.getJsonObject("body").toString());
                        break;
                    case Utils.APPLICATION_OCTET_STREAM:
                        body = message.getBinary("body");
                        break;
                    case Utils.TEXT_PLAIN:
                    default:
                        body = encode(encoding, message.getString("body"));
                }
            } else {
                body = encode(encoding, message.getString("body"));
            }

            channel.basicPublish(exchangeName, routingKey, fromJson(properties), body);
        });
    }

    @Override
    public Future<AMQP.Exchange.DeclareOk> exchangeDeclare(AMQP.Exchange.Declare exchangeDeclare) {
        return withChannel(channel -> channel.exchangeDeclare(
            exchangeDeclare.getExchange(),
            exchangeDeclare.getType(),
            exchangeDeclare.getDurable(),
            exchangeDeclare.getAutoDelete(),
            exchangeDeclare.getArguments()));

    }

    @Override
    public Future<AMQP.Queue.DeclareOk> queueDeclare(AMQP.Queue.Declare queueDeclare) {
        return withChannel(channel -> channel.queueDeclare(
            queueDeclare.getQueue(),
            queueDeclare.getDurable(),
            queueDeclare.getExclusive(),
            queueDeclare.getAutoDelete(),
            queueDeclare.getArguments()));
    }

    @Override
    public Future<Long> messageCount(String queueName) {
        return withChannel(channel -> channel.messageCount(queueName));
    }


    @Override
    public Future<AMQP.Queue.BindOk> queueBind(String queueName, String exchangeName, String routingKey) {
        return withChannel(channel -> channel.queueBind(queueName, exchangeName, routingKey));
    }

    @Override
    public void shutdownCompleted(ShutdownSignalException cause) {
        log.debug("Shutdown signal received {}" , cause);
    }

    @Override
    public void close() throws Exception {
        disconnect();
    }

    private <T> Future<T> withChannel(ChannelHandler<T> channelHandler) {
        Future<T> channelFuture = checkChannel();

        vertx.executeBlocking(future -> {
            try {
                T t = channelHandler.handle(channel);
                future.complete(t);
            } catch (Throwable t) {
                future.fail(t);
            }
        }, channelFuture);
        return channelFuture;
    }

    private <T> Future<T> checkChannel() {
        Future<T> channelFuture = Future.future();
        if (connection == null || channel == null) {
            channelFuture.fail("Not connected");
        }
        if (!channel.isOpen()) {
            channelFuture.fail("Channel is not open");
        }
        return channelFuture;
    }

    private Future<Void> withVoidChannel(ChannelConsumer channelConsumer) {
        Future<Void> channelFuture = checkChannel();

        vertx.executeBlocking(future -> {
            try {
                channelConsumer.handle(channel);
                future.complete();
            } catch (Exception t) {
                future.fail(t);
            }
        }, channelFuture);
        return channelFuture;
    }

    private interface ChannelHandler<T> {

        T handle(Channel channel) throws Exception;
    }

    private interface ChannelConsumer {

        void handle(Channel channel) throws Exception;
    }
}
