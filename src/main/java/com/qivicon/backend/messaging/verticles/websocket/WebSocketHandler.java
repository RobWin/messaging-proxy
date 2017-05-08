package com.qivicon.backend.messaging.verticles.websocket;

import com.qivicon.backend.messaging.verticles.events.Events;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.DeliveryOptions;
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
        String clientId = getClientId(serverWebSocket);
        LOG.info("WebSocket connection opened for client '{}'", clientId);
        eventBus.publish(Events.WEBSOCKET_CONNECTION_OPENED, clientId);
        MessageConsumer<JsonObject> outboundMessageConsumer = handleOutboundMessages(serverWebSocket, clientId);
        handleInboundMessages(serverWebSocket, clientId);
        serverWebSocket.exceptionHandler(exception -> LOG.warn("WebSocket failed", exception));
        serverWebSocket.closeHandler((Void) -> {
            LOG.debug("WebSocket connection closed by client");
            outboundMessageConsumer.unregister(createHandler(String.format("Outbound message consumer un-registration for client '%s'", clientId)));
            eventBus.publish(Events.WEBSOCKET_CONNECTION_CLOSED, clientId);
        });
    }

    private String getClientId(ServerWebSocket serverWebSocket) {
        return serverWebSocket.headers().get("clientId");
    }

    private void handleInboundMessages(ServerWebSocket serverWebSocket, String clientId) {
        serverWebSocket.handler(message -> {
            JsonObject jsonObject = message.toJsonObject();
            DeliveryOptions deliveryOptions = new DeliveryOptions().addHeader("clientId", clientId);
            LOG.debug("Message received from client: {}", jsonObject.encodePrettily());
            //DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
            eventBus.send(Events.WEBSOCKET_INBOUND_MESSAGE, jsonObject, deliveryOptions);
        });
    }

    private MessageConsumer<JsonObject> handleOutboundMessages(ServerWebSocket serverWebSocket, String clientId) {
        MessageConsumer<JsonObject> outboundMessageConsumer = eventBus.consumer(Events.createOutboundMessageAddress(clientId));
        outboundMessageConsumer.completionHandler(createHandler(String.format("Outbound message consumer registration for client '%s'", clientId)));
        outboundMessageConsumer.handler(message ->
            {
                JsonObject response = new JsonObject().put("body", message.body().getString("body"));
                serverWebSocket.writeTextMessage(response.encode());
                LOG.info("Outbound message sent to client '{}'", clientId);
                message.reply(String.format("Outbound message sent to client '%s'", clientId));
            }
        );
        return outboundMessageConsumer;
    }
}
