package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.verticles.ApplicationVerticle;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;

import static com.qivicon.backend.messaging.utils.LoggingUtils.configureLogging;

public class ApplicationLauncher extends io.vertx.core.Launcher {

    public static final String REGISTRY_NAME = "metricsRegistry";

    /**
     * Main entry point.
     *
     * @param args the user command line arguments.
     */
    public static void main(String[] args) {
        configureLogging();
        new ApplicationLauncher().dispatch(args);
    }

    public void beforeStartingVertx(VertxOptions options){
        options.setMetricsOptions(new DropwizardMetricsOptions()
                .setRegistryName(REGISTRY_NAME)
                .setEnabled(true));
    }

    @Override
    protected String getMainVerticle(){
        return ApplicationVerticle.class.getName();
    }

    @Override
    protected String getDefaultCommand(){
        return "run";
    }
}
