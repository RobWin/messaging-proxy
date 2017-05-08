package com.qivicon.backend.messaging.client.impl;

import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.client.AmqpClient;
import com.qivicon.backend.messaging.client.AmqpConnection;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.impl.StandardMetricsCollector;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

public class AmqpClientImpl implements AmqpClient {

    private static final Logger LOG = LoggerFactory.getLogger(AmqpClientImpl.class);

    private final ConnectionFactory connectionFactory;
    private final Vertx vertx;
    public static final String CONFIG_PREXIX = "RABBITMQ_";
    private final int retries;
    private final int retryDelay;
    private List<AmqpConnection> connections;

    public AmqpClientImpl(Vertx vertx, MetricRegistry metricRegistry){
        this.vertx = vertx;
        this.connectionFactory = new ConnectionFactory();
        this.retries = 3;
        this.retryDelay = 10000; // Every 10 seconds by default
        this.connectionFactory.setMetricsCollector(new StandardMetricsCollector(metricRegistry));
        this.connections = new ArrayList<>();
    }

    public AmqpClientImpl(Vertx vertx){
        this(vertx, new MetricRegistry());
    }

    @Override
    public Future<AmqpConnection> connect(String host, int port, String username, String password, boolean ssl) {
        if (host != null) {
            connectionFactory.setHost(host);
        }
        connectionFactory.setPort(ConnectionFactory.portOrDefault(port, ssl));
        if(username != null){
            connectionFactory.setUsername(username);
        }
        if(password != null){
            connectionFactory.setPassword(password);
        }
        if(ssl){
            try {
                connectionFactory.useSslProtocol();
            } catch (Exception e) {
                return Future.failedFuture(e);
            }
        }

        Future<AmqpConnection> startFuture = Future.future();
        vertx.executeBlocking(future -> {
            try {
                future.complete(newConnection());
            } catch (IOException | TimeoutException e) {
                LOG.error(String.format("Could not connect to RabbitMQ [%s:%d]",
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

    @Override
    public Future<AmqpConnection> connect(String host, int port, String username, String password) {
        return connect(host, port, username, password, false);
    }

    @Override
    public Future<AmqpConnection> connect(String host, int port) {
        return connect(host, port, null, null, false);
    }

    @Override
    public Future<AmqpConnection> connect(String host) {
        return connect(host, -1);
    }

    @Override
    public Future<Void> close() {
        LOG.info("Close {} AMQP connection(s) ", connections.size());
        Future<Void> closeFuture = Future.future();
        CompositeFuture.all(connections.stream()
                .map(AmqpConnection::close).collect(Collectors.toList()))
            .setHandler(event -> {
                if(event.succeeded()){
                    connections.clear();
                    closeFuture.complete();
                }else{
                    closeFuture.fail(event.cause());
                }
            });
        return closeFuture;
    }

    private AmqpConnectionImpl newConnection() throws IOException, TimeoutException {
        Connection connection = connectionFactory.newConnection();
        AmqpConnectionImpl amqpConnection = new AmqpConnectionImpl(vertx, connection);
        LOG.info("Connected to RabbitMQ [{}:{}]", connectionFactory.getHost(),
                connectionFactory.getPort());
        connections.add(amqpConnection);
        return amqpConnection;
    }

    private void reconnect(Future<AmqpConnection> future) throws IOException {
        LOG.info("Attempting to reconnect to RabbitMQ [{}:{}]",
                connectionFactory.getHost(),
                connectionFactory.getPort());
        AtomicInteger attempts = new AtomicInteger(0);
        int retries = this.retries;
        vertx.setPeriodic(retryDelay, id -> {
            int attempt = attempts.incrementAndGet();
            if (attempt == retries) {
                vertx.cancelTimer(id);
                LOG.info("Max number of connection attempts (" + retries + ") reached. Will not attempt to connect again");
                future.fail("Max number of connect attempts (" + retries + ") reached. Will not attempt to connect again");
            } else {
                try {
                    LOG.debug("Reconnect attempt # " + attempt);
                    AmqpConnectionImpl connection = newConnection();
                    connection.setRetryAttempts(attempt);
                    vertx.cancelTimer(id);
                    LOG.info("Successfully reconnected to RabbitMQ [{}:{}] (attempt # " + attempt + ")",
                            connectionFactory.getHost(),
                            connectionFactory.getPort());
                    future.complete(connection);
                } catch (IOException | TimeoutException e) {
                    LOG.debug("Failed to connection attempt # " + attempt, e);
                }
            }
        });
    }

}
