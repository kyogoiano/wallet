package br.com.wallet.edge.internal.journal;

/**
 * Thrown when a journal segment or record fails integrity verification (CRC32C mismatch, bad magic, or corrupted framing).
 * In accordance with REQ-EDG-018, corrupted segments halt recovery and are isolated for forensic analysis.
 */
public class CorruptedJournalException extends RuntimeException {

    public CorruptedJournalException(String message) {
        super(message);
    }

    public CorruptedJournalException(String message, Throwable cause) {
        super(message, cause);
    }
}
