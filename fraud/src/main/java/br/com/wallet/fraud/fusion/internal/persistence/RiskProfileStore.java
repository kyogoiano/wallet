package br.com.wallet.fraud.fusion.internal.persistence;

import br.com.wallet.fraud.fusion.api.model.RiskProfile;
import br.com.wallet.fraud.fusion.api.model.RiskSubject;
import org.jspecify.annotations.NonNull;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage-agnostic abstraction for hot state risk profiles (I-FUSION-006).
 */
public interface RiskProfileStore {

    void putProfile(@NonNull RiskSubject subject, @NonNull RiskProfile profile, @NonNull Duration ttl);

    @NonNull
    Optional<RiskProfile> getProfile(@NonNull RiskSubject subject);

    void evictProfile(@NonNull RiskSubject subject);
}
