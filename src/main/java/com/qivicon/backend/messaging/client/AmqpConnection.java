package com.qivicon.backend.messaging.client;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;

public interface AmqpConnection {

    /**
     * Gets the hostname
     *
     * @return the hostname
     */
    String getHost();

    /**
     * Gets the port
     *
     * @return the port
     */
    int getPort();

    /**
     * Closes the AMQP connection
     */
    Future<Void> close();

    /**
     * Create a new channel, using an internally allocated channel number.
     *
     * @return a new Amqp channel
     */
    Future<AmqpChannel> createChannel();

    /**
     * Sets a handler for when the connection is closed
     *
     * @param closeHandler the handler
     * @return the connection
     */
    AmqpConnection closeHandler(Handler<AsyncResult<AmqpConnection>> closeHandler);
}
