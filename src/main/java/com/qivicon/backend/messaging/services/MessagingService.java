package com.qivicon.backend.messaging.services;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface MessagingService {

    /**
     * Invoked on startup of the MessagingService.
     * <p>
     * Subclasses may use this method to allocate any resources required for the MessagingService.
     *
     * @return The future to inform about the outcome of the startup process.
     */
    Future<Void> start();

    /**
     * Invoked on shutdown of the MessagingService.
     * <p>
     * Subclasses should use this method to free up any resources allocated during connect up.
     *
     * @return  The future to inform about the outcome of the shutdown process.
     */
    Future<Void> stop();

    /**
     * Invoked when an upstream client connects.
     *
     * @param clientId The ID of the client.
     * @return The future to inform about the outcome.
     */
    Future<Void> onClientConnect(String clientId);

    /**
     * Invoked when an upstream client disconnects.
     *
     * @param clientId The ID of the client.
     * @return The future to inform about the outcome.
     */
    Future<Void> onClientDisconnect(String clientId);

    /**
     * Processes a message received from an upstream client.
     *
     * @param clientId The ID of the client.
     * @param message The message to process.
     * @return The future to inform about the outcome.
     */
    Future<Void> processMessage(String clientId, JsonObject message);
}
