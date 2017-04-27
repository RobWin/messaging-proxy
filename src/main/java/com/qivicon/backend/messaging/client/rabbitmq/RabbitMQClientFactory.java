package com.qivicon.backend.messaging.client.rabbitmq;

import com.codahale.metrics.MetricRegistry;
import com.qivicon.backend.messaging.client.MessagingClient;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

import java.util.function.Supplier;

public class RabbitMQClientFactory implements Supplier<MessagingClient> {

    private final Vertx vertx;
    private final JsonObject config;
    private final MetricRegistry metricRegistry;

    private RabbitMQClientFactory(Vertx vertx, MetricRegistry metricRegistry, JsonObject config) {
        this.vertx = vertx;
        this.config = config;
        this.metricRegistry = metricRegistry;
    }

    public static RabbitMQClientFactory create(Vertx vertx, MetricRegistry metricRegistry, JsonObject config){
        return new RabbitMQClientFactory(vertx, metricRegistry, config);
    }

    public MessagingClient get() {
        return RabbitMQClient.create(vertx, metricRegistry, config);
    }
}