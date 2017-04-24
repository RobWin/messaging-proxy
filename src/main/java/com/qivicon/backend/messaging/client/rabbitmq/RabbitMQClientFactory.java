package com.qivicon.backend.messaging.client.rabbitmq;

import com.qivicon.backend.messaging.client.MessagingClient;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.function.Supplier;

public class RabbitMQClientFactory implements Supplier<MessagingClient> {

    private Vertx vertx;
    private JsonObject config;

    private RabbitMQClientFactory(Vertx vertx, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
    }

    public static RabbitMQClientFactory create(Vertx vertx, JsonObject config){
        return new RabbitMQClientFactory(vertx, config);
    }

    public MessagingClient get() {
        return RabbitMQClient.create(vertx, config);
    }
}