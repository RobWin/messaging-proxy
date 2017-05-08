package com.qivicon.backend.messaging.client;


import com.rabbitmq.client.AMQP;
import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;

public interface AmqpChannel {

    /**
     * Closes the AMQP channel
     */
    Future<Void> close();

    /**
     * Retrieve this channel's channel number.
     * @return the channel number
     */
    int getChannelNumber();

    /**
     * Retrieve the AMQP connection which carries this channel.
     * @return the underlying {@link AmqpConnection}
     */
    AmqpConnection getConnection();

    /**
     * Sets a handler for when the channel is closed
     *
     * @param closeHandler the handler
     * @return the connection
     */
    AmqpChannel closeHandler(Handler<AsyncResult<AmqpChannel>> closeHandler);

    Future<AmqpChannel> basicConsume(String queueName, String eventBusAddress);

    Future<AmqpChannel> basicCancel(String consumerTag);

    Future<AmqpChannel> basicConsume(String queueName, String eventBusAddress, String consumerTag);

    Future<AmqpChannel> basicPublish(String exchangeName, String routingKey, JsonObject message);

    Future<AmqpChannel> exchangeDeclare(AMQP.Exchange.Declare exchange);

    Future<AmqpChannel> queueDeclare(AMQP.Queue.Declare queue);

    /*
    Future<AmqpChannel> messageCount(String queueName, Handler<AsyncResult<Long>> resultHandler);
    */

    Future<AmqpChannel> queueBind(String queueName, String exchangeName, String routingKey);
}
