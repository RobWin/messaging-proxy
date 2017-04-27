package com.qivicon.backend.messaging.verticles.metrics;

import com.codahale.metrics.MetricRegistry;
import io.prometheus.client.CollectorRegistry;
import io.prometheus.client.dropwizard.DropwizardExports;
import io.prometheus.client.exporter.common.TextFormat;
import io.vertx.core.Handler;
import io.vertx.core.buffer.Buffer;
import io.vertx.ext.web.RoutingContext;

import java.io.IOException;
import java.io.Writer;

public class PrometheusMetricsHandler implements Handler<RoutingContext> {

    /**
     * Wrap a Vert.x Buffer as a Writer so it can be used with
     * TextFormat writer
     */
    private static class BufferWriter extends Writer {

        private final Buffer buffer = Buffer.buffer();

        @Override
        public void write(char[] cbuf, int off, int len) throws IOException {
            buffer.appendString(new String(cbuf, off, len));
        }

        @Override
        public void flush() throws IOException {
            // NO-OP
        }

        @Override
        public void close() throws IOException {
            // NO-OP
        }

        Buffer getBuffer() {
            return buffer;
        }
    }

    private CollectorRegistry registry;

    /**
     * Construct a PrometheusMetricsHandler for the default registry.
     */
    public PrometheusMetricsHandler(MetricRegistry metricRegistry) {
        CollectorRegistry collectorRegistry = new CollectorRegistry();
        collectorRegistry.register(new DropwizardExports(metricRegistry));
        this.registry = collectorRegistry;
    }

    /**
     * Construct a PrometheusMetricsHandler for the given registry.
     */
    public PrometheusMetricsHandler(CollectorRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void handle(RoutingContext ctx) {
        try {
            final BufferWriter writer = new BufferWriter();
            TextFormat.write004(writer, registry.metricFamilySamples());
            ctx.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", TextFormat.CONTENT_TYPE_004)
                    .end(writer.getBuffer());
        } catch (IOException e) {
            ctx.fail(e);
        }
    }
}