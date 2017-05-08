package com.qivicon.backend.messaging.client;

import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.client.impl.AmqpClientImpl;
import io.vertx.core.Future;
import io.vertx.core.Vertx;

public interface AmqpClient {

    /**
     * Create a AmqpClient instance with the given Vertx instance.
     *
     * @param vertx the vertx instance to use
     * @return the client instance
     */
    static AmqpClient create(Vertx vertx) {
        return new AmqpClientImpl(vertx);
    }

    /**
     * Create a AmqpClient instance with the given Vertx instance.
     *
     * @param vertx the vertx instance to use
     * @param vertx the metric registry to use
     * @return the client instance
     */
    static AmqpClient create(Vertx vertx, MetricRegistry metricRegistry) {
        return new AmqpClientImpl(vertx, metricRegistry);
    }

    /**
     * Connect to the given host and port
     *
     * @param host
     *          the host to connect to
     * @param port
     *          the port to connect to
     * @param username
     *          the user name
     * @param password the password
     * @param ssl
     *          use ssl
     * @return future with either the opened AmqpConnection or failure cause.
     */
    Future<AmqpConnection> connect(String host, int port, String username, String password, boolean ssl);

    /**
     * Connect to the given host and port without ssl.
     *
     * @param host
     *          the host to connect to
     * @param port
     *          the port to connect to
     * @param username
     *          the user name
     * @param password
     *          the password
     * @return future with either the opened AmqpConnection or failure cause.
     */
    Future<AmqpConnection> connect(String host, int port, String username, String password);

    /**
     * Connect to the given host and port with default credentials (guest/guest).
     *
     * @param host
     *          the host to connect to
     * @param port
     *          the port to connect to
     * @return future with either the opened AmqpConnection or failure cause.
     */
    Future<AmqpConnection> connect(String host, int port);

    /**
     * Connect to the given host with default port and credentials (guest/guest).
     *
     * @param host
     *          the host to connect to
     * @return future with either the opened AmqpConnection or failure cause.
     */
    Future<AmqpConnection> connect(String host);

    /**
     * Closes all AMQP connections
     */
    Future<Void> close();


}
