package com.qivicon.backend.messaging.client.impl.consumer;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.DefaultConsumer;
import com.rabbitmq.client.Envelope;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.MessageProducer;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

import static com.qivicon.backend.messaging.client.impl.utils.Utils.*;


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
        LOG.debug("Created consumer for channel {} which sends consumed messages to event bus address '{}'", channel.getChannelNumber(), eventBusAddress);
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
            vertx.runOnContext(v -> {
                long deliveryTag = envelope.getDeliveryTag();
                DeliveryOptions deliveryOptions = new DeliveryOptions().setSendTimeout(5000);
                MessageProducer<JsonObject> sender = vertx.eventBus().sender(eventBusAddress, deliveryOptions);
                sender.exceptionHandler(exception -> rejectMessage(deliveryTag, exception));
                LOG.info("Send message to to address '{}'", eventBusAddress);
                sender.send(msg, replyEvent -> {
                    if(replyEvent.succeeded()){
                        acknowledgeMessage(deliveryTag);
                    }else{
                        rejectMessage(deliveryTag, replyEvent.cause());
                    }
                });
            });

        } catch (UnsupportedEncodingException e) {
            LOG.error(String.format("Failed to parse message in consumer with consumerTag '%s'", consumerTag), e);
        }
    }

    private void acknowledgeMessage(long deliveryTag) {
        try {
            getChannel().basicAck(deliveryTag, false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void rejectMessage(long deliveryTag, Throwable exception) {
        try {
            LOG.warn(String.format("Failed to send message to address '%s' in consumer with consumerTag '%s'", eventBusAddress, getConsumerTag()),
                    exception);
            getChannel().basicNack(deliveryTag, false, true);
        } catch (IOException ioException) {
            LOG.warn(String.format("Failed to Nack message with to deliveryTag '%s'", deliveryTag),
                    ioException);
        }
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {
        LOG.debug("Consumer has been cancelled unexpectedly");
    }
}
