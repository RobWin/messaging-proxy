package com.qivicon.backend.messaging.rabbitmq;

import io.vertx.core.Future;

public interface MessagingService {

    /**
     * Invoked on startup of the MessagingService.
     * <p>
     * Subclasses may use this method to allocate any resources required for the MessagingService.
     * <p>
     * Clients <em>must not</em> invoke any of the other methods before the
     * start future passed in to this method has completed successfully.
     *
     * @param startFuture The handler to inform about the outcome of the startup process.
     */
    void start(Future<Void> startFuture);

    /**
     * Invoked on shutdown of the MessagingService.
     * <p>
     * Subclasses should use this method to free up any resources allocated during start up.
     * <p>
     * Clients <em>must not</em> invoke any of the other methods after this
     * method has completed successfully.
     *
     * @param stopFuture The handler to inform about the outcome of the shutdown process.
     */
    void stop(Future<Void> stopFuture);

    /**
     * Invoked when an upstream client connects.
     *
     * @param clientId The ID of the client.
     */
    void onClientConnect(String clientId);

    /**
     * Invoked when an upstream client disconnects.
     *
     * @param clientId The ID of the client.
     */
    void onClientDisconnect(String clientId);

    /**
     * Processes a message received from an upstream client.
     *
     * @param message The message to process.
     */
    void processMessage(String message);
}
