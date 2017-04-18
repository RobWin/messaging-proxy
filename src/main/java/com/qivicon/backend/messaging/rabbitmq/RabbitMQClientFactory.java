package com.qivicon.backend.messaging.rabbitmq;

import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.rabbitmq.RabbitMQClient;

public class RabbitMQClientFactory {

    public static RabbitMQClientFactory RABBITMQ_CLIENT_FACTORY_INSTANCE = new RabbitMQClientFactory();

    private static RabbitMQClient rabbitClient;

    private RabbitMQClientFactory() {}

    public RabbitMQClient getRabbitClient(Vertx vertx) {
        if (rabbitClient == null) {
            JsonObject config = new JsonObject();
            config.put("uri", System.getenv("AMQP_URL"));
            config.put("connectionTimeout", 50000);
            config.put("handshakeTimeout", 50000);
            rabbitClient = RabbitMQClient.create(vertx, config);
        }
        return rabbitClient;
    }

}