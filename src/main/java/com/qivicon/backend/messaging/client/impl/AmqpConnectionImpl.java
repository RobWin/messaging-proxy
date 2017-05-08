package com.qivicon.backend.messaging.client.impl;

import com.qivicon.backend.messaging.client.AmqpChannel;
import com.qivicon.backend.messaging.client.AmqpConnection;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpConnectionImpl implements AmqpConnection, ShutdownListener {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpConnectionImpl.class);

    private final Vertx vertx;
    private final Connection connection;
    private int retryAttempts;

    private Handler<AsyncResult<AmqpConnection>> closeHandler = (result) -> {
        if (result.succeeded()) {
            AmqpConnection connection = result.result();
            LOG.debug("Connection shutdown to RabbitMQ [{}:{}] completed",
                    connection.getHost(),
                    connection.getPort());
        } else {
            LOG.warn("Connection shutdown failed", result.cause());
        }
    };

    public AmqpConnectionImpl(Vertx vertx, Connection connection) {
        this.vertx = vertx;
        this.connection = connection;
        this.connection.addShutdownListener(this);
    }

    @Override
    public void shutdownCompleted(ShutdownSignalException e) {
        LOG.info("Connection shutdown to RabbitMQ [{}:{}] completed. AMQP Method: {}",
                connection.getAddress().getHostName(),
                connection.getPort(),
                e.getReason().protocolMethodName());
        closeHandler.handle(Future.succeededFuture(this));
    }

    public int getRetryAttempts() {
        return retryAttempts;
    }

    void setRetryAttempts(int retryAttempts) {
        this.retryAttempts = retryAttempts;
    }

    @Override
    public String getHost() {
        return connection.getAddress().getHostName();
    }

    @Override
    public int getPort() {
        return connection.getPort();
    }

    @Override
    public Future<Void> close() {
        Future<Void> closeFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                connection.close();
                LOG.info("Closed connection to RabbitMQ [{}:{}]",
                        connection.getAddress().getHostName(),
                        connection.getPort());
                future.complete();
            } catch (Exception e) {
                future.fail(e);
            }
        }, closeFuture);
        return closeFuture;
    }

    @Override
    public Future<AmqpChannel> createChannel() {
        Future<AmqpChannel> channelFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                Channel channel = connection.createChannel();
                LOG.info("Created channel #{} to RabbitMQ [{}:{}]",
                        channel.getChannelNumber(),
                        connection.getAddress().getHostName(),
                        connection.getPort());
                future.complete(new AmqpChannelImpl(vertx, this, channel));
            } catch (Exception e) {
                future.fail(e);
            }
        }, channelFuture);
        return channelFuture;
    }

    @Override
    public AmqpConnection closeHandler(Handler<AsyncResult<AmqpConnection>> closeHandler) {
        this.closeHandler = closeHandler;
        return this;
    }
}
