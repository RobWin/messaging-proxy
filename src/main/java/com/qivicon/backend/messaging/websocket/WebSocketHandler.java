package com.qivicon.backend.messaging.websocket;

import com.qivicon.backend.messaging.events.Events;
import io.vertx.core.Handler;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.ServerWebSocket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WebSocketHandler implements Handler<ServerWebSocket> {

    private static final Logger LOG = LoggerFactory.getLogger(WebSocketHandler.class);

    private final EventBus eventBus;

    public WebSocketHandler(final EventBus eventBus){
        this.eventBus = eventBus;
    }

    @Override
    public void handle(ServerWebSocket serverWebSocket) {
        LOG.debug("WebSocket connection opened");
        eventBus.publish(Events.WEBSOCKET_CONNECTION_OPENED, "Home Base ID");
        eventBus.<String>consumer(Events.WEBSOCKET_OUTBOUND_MESSAGE, message ->
                serverWebSocket.writeTextMessage(message.body())
        );
        serverWebSocket.handler(message -> {
            LOG.debug("Message received from client: {}", message.toString());
            //DeliveryOptions options = new DeliveryOptions().setSendTimeout(5000);
            eventBus.send(Events.WEBSOCKET_INBOUND_MESSAGE, message.toString());
        });
        serverWebSocket.exceptionHandler(exception -> LOG.warn("WebSocket failed", exception));
        serverWebSocket.closeHandler((Void) -> {
            LOG.debug("WebSocket connection closed by client");
            eventBus.publish(Events.WEBSOCKET_CONNECTION_CLOSED, "Home Base ID");
        });
    }
}
