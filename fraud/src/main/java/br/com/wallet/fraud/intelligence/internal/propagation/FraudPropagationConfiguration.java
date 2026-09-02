package br.com.wallet.fraud.intelligence.internal.propagation;

import br.com.wallet.fraud.intelligence.propagation.PropagationConfig;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring configuration providing default beans for Fraud Risk Propagation.
 */
@Configuration
public class FraudPropagationConfiguration {

    @Bean
    @ConditionalOnMissingBean(PropagationConfig.class)
    public PropagationConfig propagationConfig() {
        return PropagationConfig.defaultConfig();
    }
}
