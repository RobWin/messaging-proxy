
package com.qivicon.backend.messaging.verticles.consumer;

import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsumerRegistrationHandler<E> implements Handler<AsyncResult<E>> {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumerRegistrationHandler.class);

    private final String logMessage;

    private ConsumerRegistrationHandler(String logMessage){
        this.logMessage = logMessage;
    }

    @Override
    public void handle(AsyncResult<E> event) {
        if(event.succeeded()){
            LOG.debug(logMessage + " succeeded");
        }else{
            LOG.warn(logMessage + " failed", event.cause());
        }
    }

    public static <E> ConsumerRegistrationHandler<E> createHandler(String logMessage) {
        return new ConsumerRegistrationHandler<>(logMessage);
    }
}
