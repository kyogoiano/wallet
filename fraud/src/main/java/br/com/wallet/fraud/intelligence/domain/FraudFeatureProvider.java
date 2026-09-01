package br.com.wallet.fraud.intelligence.domain;

import org.jspecify.annotations.NonNull;

import java.time.Instant;
import java.util.UUID;

public interface FraudFeatureProvider {
    @NonNull GraphRiskSignals evaluateGraphSignals(@NonNull UUID entityId, @NonNull Instant asOf);
}
