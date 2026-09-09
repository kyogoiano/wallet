package br.com.wallet.fraud.fusion.internal.gate;

import br.com.wallet.fraud.fusion.api.FraudGate;
import br.com.wallet.fraud.fusion.api.model.FraudDecision;
import br.com.wallet.fraud.fusion.api.model.GateAuthorizationResult;
import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import br.com.wallet.fraud.fusion.internal.persistence.RiskProfileStore;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * High-performance pre-execution authorization gate meeting the P99 < 2ms Gateway SLA (REQ-FUSION-007).
 */
@Component
public class FraudGateV4 implements FraudGate {

    private static final Logger log = LoggerFactory.getLogger(FraudGateV4.class);
    public static final BigDecimal HIGH_VALUE_THRESHOLD = new BigDecimal("5000.00");

    private final RiskProfileStore profileStore;

    public FraudGateV4(@NonNull final RiskProfileStore profileStore) {
        this.profileStore = Objects.requireNonNull(profileStore, "profileStore cannot be null");
    }

    @NonNull
    public GateAuthorizationResult authorize(@NonNull final RiskSubject subject, @NonNull final BigDecimal amount) {
        Objects.requireNonNull(subject, "subject cannot be null");
        Objects.requireNonNull(amount, "amount cannot be null");

        Optional<RiskProfile> profileOpt = profileStore.getProfile(subject);

        if (profileOpt.isEmpty()) {
            return handleMissingProfile(subject, amount);
        }

        RiskProfile profile = profileOpt.get();

        if (profile.status() == FraudDecision.HARD_BLOCK) {
            log.warn("Blocking transaction for subject {}: direct rule hard block", subject.toKey());
            return GateAuthorizationResult.block(FraudDecision.HARD_BLOCK, "HARD_BLOCK: " + profile.primaryDriver());
        }

        if (profile.status() == FraudDecision.RESTRICT) {
            log.warn("Restricting transaction for subject {}: high fused risk {}", subject.toKey(), profile.finalRisk());
            return GateAuthorizationResult.block(FraudDecision.RESTRICT, "RESTRICT: " + profile.primaryDriver());
        }

        if (profile.status() == FraudDecision.REVIEW) {
            log.info("Subject {} is under review (score={}), authorizing conditionally", subject.toKey(), profile.finalRisk());
            return GateAuthorizationResult.allow("REVIEW_PERMITTED: " + profile.primaryDriver());
        }

        return GateAuthorizationResult.allow("AUTHORIZED");
    }

    private GateAuthorizationResult handleMissingProfile(RiskSubject subject, BigDecimal amount) {
        if (amount.compareTo(HIGH_VALUE_THRESHOLD) >= 0) {
            log.warn("High value transaction {} for unprofiled subject {}: fail-closed", amount, subject.toKey());
            return GateAuthorizationResult.block(FraudDecision.RESTRICT, "FAIL_CLOSED: High value unprofiled entity");
        }

        log.debug("Unprofiled entity {} for normal amount {}: deterministic fallback allow", subject.toKey(), amount);
        return GateAuthorizationResult.allow("FALLBACK_PERMITTED");
    }
}
