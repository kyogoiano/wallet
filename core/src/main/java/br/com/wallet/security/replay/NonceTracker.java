package br.com.wallet.security.replay;

/**
 * Service Provider Interface (SPI) for two-phase nonce admission and replay protection (REQ-SEC-028, REQ-SEC-029, I-ENV-004).
 *
 * <p>Phase 1: {@link #reserve(ReplayKey)} reserves the nonce under a short lease (e.g. 10s).
 * <p>Phase 2: Upon durable journal persistence, {@link #commit(ReplayKey)} commits the nonce for the full replay window (e.g. 60s).
 * <p>Rollback: If pre-journal validation or KMS operations fail, {@link #release(ReplayKey)} releases the temporary lease
 * so legitimate client retries succeed immediately without false-positive 401 rejections.
 */
public interface NonceTracker {

    /**
     * Attempts to acquire a short-lived temporary lease on the nonce.
     *
     * @param key The unique tenant and principal replay key.
     * @return {@link NonceReservation.Admitted} if reserved, {@link NonceReservation.Rejected} if already seen,
     *         or {@link NonceReservation.Unavailable} if storage is unreachable.
     */
    NonceReservation reserve(ReplayKey key);

    /**
     * Commits the nonce lease to permanent replay protection once the command has been durably fsynced to disk.
     *
     * @param key The unique tenant and principal replay key.
     */
    void commit(ReplayKey key);

    /**
     * Releases the temporary reservation lease on early pipeline failure prior to journal fsync.
     *
     * @param key The unique tenant and principal replay key.
     */
    void release(ReplayKey key);
}
