package com.qivicon.backend.messaging.verticles.events;

public class Events {

    public static final String WEBSOCKET_CONNECTION_CLOSED = "websocket.connection.closed";
    public static final String WEBSOCKET_CONNECTION_OPENED = "websocket.connection.opened";
    public static final String WEBSOCKET_INBOUND_MESSAGE = "websocket.inbound.message";
    public static final String WEBSOCKET_OUTBOUND_MESSAGE = "websocket.outbound.message";

    public static String createOutboundMessageAddress(String clientId){
        return WEBSOCKET_OUTBOUND_MESSAGE + "." + clientId;
    }
}
