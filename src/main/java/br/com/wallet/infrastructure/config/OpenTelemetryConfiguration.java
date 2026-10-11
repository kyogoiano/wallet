package br.com.wallet.infrastructure.config;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmClassLoadingMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmCpuMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmMemoryMeterConventions;
import io.micrometer.core.instrument.binder.jvm.convention.otel.OpenTelemetryJvmThreadMeterConventions;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.observation.ObservationPredicate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.observation.OpenTelemetryServerRequestObservationConvention;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import java.util.List;

@Configuration(proxyBeanMethods = false)
public class OpenTelemetryConfiguration {

    @Bean
    OpenTelemetryServerRequestObservationConvention openTelemetryServerRequestObservationConvention() {
        return new OpenTelemetryServerRequestObservationConvention();
    }

    @Bean
    OpenTelemetryJvmCpuMeterConventions openTelemetryJvmCpuMeterConventions() {
        return new OpenTelemetryJvmCpuMeterConventions(Tags.empty());
    }

    @Bean
    ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics(
                List.of(),
                new OpenTelemetryJvmCpuMeterConventions(Tags.empty())
        );
    }

    @Bean
    JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics(
                List.of(),
                new OpenTelemetryJvmMemoryMeterConventions(Tags.empty())
        );
    }

    @Bean
    JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics(
                List.of(),
                new OpenTelemetryJvmThreadMeterConventions(Tags.empty())
        );
    }

    @Bean
    ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics(
                new OpenTelemetryJvmClassLoadingMeterConventions()
        );
    }

    /**
     * History 11: Reduce metric cardinality at the source by ignoring verbose framework bean tags.
     */
    @Bean
    MeterFilter ignoreSpringBeanName() {
        return MeterFilter.ignoreTags("spring.bean.name");
    }

    /**
     * History 11: Filter out repetitive health check observation spans at the source.
     */
    @Bean
    ObservationPredicate noActuatorObservations() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                if(serverContext.getCarrier() != null) {
                    String uri = serverContext.getCarrier().getRequestURI();
                    return uri == null || !uri.startsWith("/actuator/health");
                }
            }
            return true;
        };
    }
}
