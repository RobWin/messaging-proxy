package com.qivicon.backend.messaging;

import com.qivicon.backend.messaging.verticles.ApplicationVerticle;
import io.vertx.core.VertxOptions;
import io.vertx.ext.dropwizard.DropwizardMetricsOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.qivicon.backend.messaging.logging.LoggingUtils.configureLogging;

public class ApplicationLauncher extends io.vertx.core.Launcher {

    public static final String REGISTRY_NAME = "metricsRegistry";

    private static final Logger LOG = LoggerFactory.getLogger(ApplicationLauncher.class);

    /**
     * Main entry point.
     *
     * @param args the user command line arguments.
     */
    public static void main(String[] args) {
        configureLogging();
        LOG.info("Run application launcher");
        for(String arg : args){
            LOG.info("Argument: {}", arg);
        }

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
