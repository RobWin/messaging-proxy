package com.qivicon.backend.messaging.services.impl;

import com.qivicon.backend.messaging.client.MessagingClient;
import com.qivicon.backend.messaging.services.MessagingService;

import java.util.function.Supplier;


public class MessagingServiceFactory implements Supplier<MessagingService> {

    private Supplier<MessagingClient> messagingClientFactory;

    private MessagingServiceFactory(Supplier<MessagingClient> messagingClientFactory){
        this.messagingClientFactory = messagingClientFactory;
    }

    public static MessagingServiceFactory create(Supplier<MessagingClient> messagingClientFactory){
        return new MessagingServiceFactory(messagingClientFactory);
    }

    @Override
    public MessagingService get() {
        return new DefaultMessagingService(messagingClientFactory);
    }
}
