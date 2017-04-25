package com.qivicon.backend.messaging.verticles.auth;


import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.Handler;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.auth.AuthProvider;
import io.vertx.ext.auth.User;

public class QbertAuthProvider implements AuthProvider{

    @Override
    public void authenticate(JsonObject authInfo, Handler<AsyncResult<User>> resultHandler) {
        String clientId = authInfo.getString("username");
        String clientPassword = authInfo.getString("password");

        // TODO Check credentials

        resultHandler.handle(Future.succeededFuture(new HomeBase(authInfo)));
    }
}
