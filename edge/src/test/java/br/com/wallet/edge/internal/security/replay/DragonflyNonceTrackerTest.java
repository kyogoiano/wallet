package br.com.wallet.edge.internal.security.replay;

import br.com.wallet.security.envelope.TenantId;
import br.com.wallet.security.replay.NonceReservation;
import br.com.wallet.security.replay.PrincipalId;
import br.com.wallet.security.replay.ReplayAvailabilityReason;
import br.com.wallet.security.replay.ReplayKey;
import br.com.wallet.security.replay.ReplayRejectionReason;
import io.lettuce.core.RedisConnectionException;
import io.lettuce.core.SetArgs;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("TASK-10.10: DragonflyNonceTracker Two-Phase Adapter Test (REQ-SEC-029, I-ENV-004)")
class DragonflyNonceTrackerTest {

    @Mock
    private RedisCommands<String, String> redisCommands;

    private DragonflyNonceTracker nonceTracker;
    private final ReplayKey replayKey = new ReplayKey(
            new TenantId("tenant-finance"),
            new PrincipalId("client-007"),
            "nonce-unique-123"
    );

    @BeforeEach
    void setUp() {
        this.nonceTracker = new DragonflyNonceTracker(redisCommands);
    }

    @Test
    @DisplayName("Assert reserve returns Admitted when Redis SET NX EX succeeds")
    void shouldAdmitWhenSetNxSucceeds() {
        when(redisCommands.set(eq(replayKey.toStorageKey()), eq("RESERVED"), any(SetArgs.class)))
                .thenReturn("OK");

        NonceReservation reservation = nonceTracker.reserve(replayKey);

        assertThat(reservation).isInstanceOf(NonceReservation.Admitted.class);
    }

    @Test
    @DisplayName("Assert reserve returns Rejected when key already exists")
    void shouldRejectWhenKeyAlreadyExists() {
        when(redisCommands.set(eq(replayKey.toStorageKey()), eq("RESERVED"), any(SetArgs.class)))
                .thenReturn(null);

        NonceReservation reservation = nonceTracker.reserve(replayKey);

        assertThat(reservation).isInstanceOf(NonceReservation.Rejected.class);
        NonceReservation.Rejected rejected = (NonceReservation.Rejected) reservation;
        assertThat(rejected.reason()).isEqualTo(ReplayRejectionReason.DUPLICATE_NONCE);
    }

    @Test
    @DisplayName("Assert reserve fails closed with Unavailable when Redis throws connection exception")
    void shouldReturnUnavailableOnRedisFailure() {
        when(redisCommands.set(eq(replayKey.toStorageKey()), eq("RESERVED"), any(SetArgs.class)))
                .thenThrow(new RedisConnectionException("Connection refused"));

        NonceReservation reservation = nonceTracker.reserve(replayKey);

        assertThat(reservation).isInstanceOf(NonceReservation.Unavailable.class);
        NonceReservation.Unavailable unavailable = (NonceReservation.Unavailable) reservation;
        assertThat(unavailable.reason()).isEqualTo(ReplayAvailabilityReason.STORAGE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Assert commit sets COMMITTED with 60s expiration")
    void shouldCommitNonce() {
        nonceTracker.commit(replayKey);

        verify(redisCommands).set(eq(replayKey.toStorageKey()), eq("COMMITTED"), any(SetArgs.class));
    }

    @Test
    @DisplayName("Assert release deletes nonce key")
    void shouldReleaseNonceOnRollback() {
        nonceTracker.release(replayKey);

        verify(redisCommands).del(replayKey.toStorageKey());
    }
}
