package com.qivicon.backend.messaging.client.impl;

import com.qivicon.backend.messaging.client.AmqpChannel;
import com.qivicon.backend.messaging.client.AmqpConnection;
import com.qivicon.backend.messaging.client.impl.utils.Utils;
import com.qivicon.backend.messaging.client.impl.consumer.EventBusForwarder;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.client.impl.utils.Utils.*;


public class AmqpChannelImpl implements AmqpChannel, ShutdownListener {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpChannelImpl.class);

    private final AmqpConnection amqpConnection;
    private final Channel channel;
    private final Vertx vertx;

    private Handler<AsyncResult<AmqpChannel>> closeHandler = (result) -> {
        if (result.succeeded()) {
            AmqpChannel channel = result.result();
            LOG.debug("Connection shutdown of channel #{} to RabbitMQ [{}:{}] completed",
                    channel.getChannelNumber(),
                    channel.getConnection().getHost(),
                    channel.getConnection().getPort());
        } else {
            LOG.warn("Channel shutdown failed", result.cause());
        }
    };

    public AmqpChannelImpl(Vertx vertx, AmqpConnection amqpConnection, Channel channel) {
        this.amqpConnection = amqpConnection;
        this.vertx = vertx;
        this.channel = channel;
        this.channel.addShutdownListener(this);
    }

    @Override
    public Future<Void> close() {
        Future<Void> closeFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                channel.close();
                LOG.info("Closed channel to RabbitMQ [{}:{}]",
                        channel.getConnection().getAddress().getHostName(),
                        channel.getConnection().getPort());
                future.complete();
            } catch (Exception e) {
                future.fail(e);
            }
        }, closeFuture);
        return closeFuture;
    }

    @Override
    public int getChannelNumber() {
        return channel.getChannelNumber();
    }

    @Override
    public AmqpConnection getConnection() {
        return amqpConnection;
    }

    @Override
    public AmqpChannel closeHandler(Handler<AsyncResult<AmqpChannel>> closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }

    @Override
    public void shutdownCompleted(ShutdownSignalException cause) {
        LOG.info("Channel #{} shutdown to RabbitMQ [{}:{}] completed. AMQP Method: {}",
                channel.getChannelNumber(),
                channel.getConnection().getAddress().getHostName(),
                channel.getConnection().getPort(),
                cause.getReason().protocolMethodName());
        closeHandler.handle(Future.succeededFuture(this));
    }

    @Override
    public Future<AmqpChannel> basicConsume(String queueName, String eventBusAddress) {
        return withChannel(channel -> channel.basicConsume(queueName, false,
                new EventBusForwarder(vertx, channel, eventBusAddress)));
    }

    @Override
    public Future<AmqpChannel> basicCancel(String consumerTag) {
        return withVoidChannel(channel -> channel.basicCancel(consumerTag));
    }

    @Override
    public Future<AmqpChannel> basicConsume(String queueName, String eventBusAddress, String consumerTag) {
        return withChannel(channel -> channel.basicConsume(queueName, false, consumerTag, new EventBusForwarder(vertx, channel, eventBusAddress)));
    }

    @Override
    public Future<AmqpChannel> basicPublish(String exchangeName, String routingKey, JsonObject message) {
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
    public Future<AmqpChannel> exchangeDeclare(AMQP.Exchange.Declare exchangeDeclare) {
        LOG.info("Declare exchange '{}'", exchangeDeclare.getExchange());
        return withChannel(channel -> channel.exchangeDeclare(
                exchangeDeclare.getExchange(),
                exchangeDeclare.getType(),
                exchangeDeclare.getDurable(),
                exchangeDeclare.getAutoDelete(),
                exchangeDeclare.getArguments()));

    }

    @Override
    public Future<AmqpChannel> queueDeclare(AMQP.Queue.Declare queueDeclare) {
        LOG.info("Declare queue '{}'", queueDeclare.getQueue());
        return withChannel(channel -> channel.queueDeclare(
                queueDeclare.getQueue(),
                queueDeclare.getDurable(),
                queueDeclare.getExclusive(),
                queueDeclare.getAutoDelete(),
                queueDeclare.getArguments()));
    }

    /*
    @Override
    public Future<AmqpChannel> messageCount(String queueName, Handler<AsyncResult<Long>> resultHandler) {
        return withChannel(channel -> channel.messageCount(queueName), resultHandler);
    }
    */

    @Override
    public Future<AmqpChannel> queueBind(String queueName, String exchangeName, String routingKey) {
        LOG.info("Create binding exchange: '{}' queue: '{}' routingKey: '{}'", exchangeName, queueName, routingKey);
        return withChannel(channel -> channel.queueBind(queueName, exchangeName, routingKey));
    }


    private <T> Future<AmqpChannel> withChannel(ChannelHandler<T> channelHandler) {
        Future<AmqpChannel> channelFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                T t = channelHandler.handle(channel);
                future.complete(this);
            } catch (Throwable t) {
                future.fail(t);
            }
        }, channelFuture);
        return channelFuture;
    }

    private Future<AmqpChannel> withVoidChannel(ChannelConsumer channelConsumer) {
        Future<AmqpChannel> channelFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                channelConsumer.handle(channel);
                future.complete(this);
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
