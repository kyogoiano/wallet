package br.com.wallet.fraud.embeddings.internal.queue;

public enum EmbeddingJobStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    RETRY_WAIT,
    FAILED
}
