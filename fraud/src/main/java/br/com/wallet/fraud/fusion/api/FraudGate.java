package br.com.wallet.fraud.fusion.api;

import br.com.wallet.fraud.fusion.api.model.GateAuthorizationResult;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import org.jspecify.annotations.NonNull;

import java.math.BigDecimal;

/**
 * Pre-execution authorization gate contract meeting the P99 < 2ms Gateway SLA (REQ-FUSION-007).
 */
public interface FraudGate {

    @NonNull
    GateAuthorizationResult authorize(@NonNull RiskSubject subject, @NonNull BigDecimal amount);
}
