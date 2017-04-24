package com.qivicon.backend.messaging.verticles.websocket;

import com.qivicon.backend.messaging.verticles.events.Events;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.core.json.JsonObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.verticles.consumer.ConsumerRegistrationHandler.createHandler;

public class WebSocketHandler implements Handler<ServerWebSocket> {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketHandler.class);

    private final EventBus eventBus;

    public WebSocketHandler(final EventBus eventBus){
        this.eventBus = eventBus;
    }

    @Override
    public void handle(ServerWebSocket serverWebSocket) {
        LOG.info("WebSocket connection opened");
        eventBus.publish(Events.WEBSOCKET_CONNECTION_OPENED, "Home Base ID");
        MessageConsumer<JsonObject> outboundMessageConsumer = handleOutboundMessages(serverWebSocket);
        handleInboundMessages(serverWebSocket);
        serverWebSocket.exceptionHandler(exception -> LOG.warn("WebSocket failed", exception));
        serverWebSocket.closeHandler((Void) -> {
            LOG.debug("WebSocket connection closed by client");
            outboundMessageConsumer.unregister(createHandler("Outbound message consumer un-registration"));
            eventBus.publish(Events.WEBSOCKET_CONNECTION_CLOSED, "Home Base ID");
        });
    }

    private void handleInboundMessages(ServerWebSocket serverWebSocket) {
        serverWebSocket.handler(message -> {
            JsonObject jsonObject = message.toJsonObject();
            LOG.debug("Message received from client: {}", jsonObject.encodePrettily());
            //DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
            eventBus.send(Events.WEBSOCKET_INBOUND_MESSAGE, jsonObject);
        });
    }

    private MessageConsumer<JsonObject> handleOutboundMessages(ServerWebSocket serverWebSocket) {
        MessageConsumer<JsonObject> outboundMessageConsumer = eventBus.consumer(Events.WEBSOCKET_OUTBOUND_MESSAGE);
        outboundMessageConsumer.completionHandler(createHandler("Outbound message consumer registration"));
        outboundMessageConsumer.handler(message ->
            serverWebSocket.writeTextMessage(message.body().encode())
        );
        return outboundMessageConsumer;
    }
}
