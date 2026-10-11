package br.com.wallet.infrastructure.config;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.server.servlet.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Headless Core runtime configuration (TASK-PRC-4.1, TASK-PRC-6.1, REQ-PRC-002, REQ-PRC-012, REQ-PRC-013).
 * Enforces port 8081 for Core management and emits prominent warning log if running in monolith mode.
 */
@Configuration
public class CoreRuntimeConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CoreRuntimeConfiguration.class);

    @Value("${wallet.runtime.mode:multi-process}")
    private String runtimeMode;

    @Value("${server.port:8081}")
    private int configuredPort;

    @PostConstruct
    public void init() {
        if ("monolith".equalsIgnoreCase(runtimeMode)) {
            log.warn("===============================================================================");
            log.warn("⚠️  WARNING: Running in MONOLITH runtime mode (REQ-PRC-012 / I-RUNTIME-001)   ⚠️");
            log.warn("⚠️  Edge and Core are sharing a single JVM process. NOT RECOMMENDED FOR PROD! ⚠️");
            log.warn("===============================================================================");
        } else {
            log.info("Starting in MULTI-PROCESS runtime mode (Core headless on port {})", configuredPort);
        }
    }

    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> corePortCustomizer(
            @Value("${wallet.runtime.mode:multi-process}") String mode,
            @Value("${server.port:8081}") int port
    ) {
        return factory -> {
            if ("multi-process".equalsIgnoreCase(mode) && port > 0) {
                factory.setPort(port);
            }
        };
    }
}
