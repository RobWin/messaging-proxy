package com.qivicon.backend.messaging.client;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Connection;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;

public interface MessagingClient {

    Future<Connection> connect();

    Future<Void> disconnect();

    Future<String> basicConsume(String queueName, String eventBusAddress);

    Future<Void> basicCancel(String consumerTag);

    Future<String> basicConsume(String queueName, String eventBusAddress, boolean autoAck, String consumerTag);

    Future<Void> basicPublish(String exchangeName, String routingKey, JsonObject message);

    Future<AMQP.Exchange.DeclareOk> exchangeDeclare(AMQP.Exchange.Declare exchange);

    Future<AMQP.Queue.DeclareOk> queueDeclare(AMQP.Queue.Declare queue);

    Future<Long> messageCount(String queueName);

    Future<AMQP.Queue.BindOk> queueBind(String queueName, String exchangeName, String routingKey);

}
