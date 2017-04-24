package com.qivicon.backend.messaging.client.rabbitmq.consumer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static com.qivicon.backend.messaging.client.rabbitmq.Utils.*;


public class EventBusForwarder extends DefaultConsumer {

    private final Vertx vertx;
    private String eventBusAddress;

    private static final Logger LOG = LoggerFactory.getLogger(EventBusForwarder.class);
    /**
     * Constructs a new instance and records its association to the passed-in channel.
     *
     * @param channel the channel to which this consumer is attached
     */
    public EventBusForwarder(Vertx vertx, Channel channel, String eventBusAddress) {
        super(channel);
        this.vertx = vertx;
        this.eventBusAddress = eventBusAddress;
        LOG.info("Created consumer for channel {} which sends consumed messages to event bus address '{}'", channel.getChannelNumber(), eventBusAddress);
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        JsonObject msg = new JsonObject();
        msg.put("consumerTag", consumerTag);

        // Add the envelope data
        populate(msg, envelope);

        // Add properties (if configured)
        put("properties", toJson(properties), msg);

        // Parse the body
        try {
            msg.put("body", parse(properties, body));
            vertx.runOnContext(v -> vertx.eventBus().send(eventBusAddress, msg));

        } catch (UnsupportedEncodingException e) {
            LOG.error(String.format("Exception occurred inside RabbitMQ MessageConsumer with consumerTag %s.", consumerTag), e);
        }
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
        LOG.debug("Consumer has been cancelled unexpectedly");
    }
}
