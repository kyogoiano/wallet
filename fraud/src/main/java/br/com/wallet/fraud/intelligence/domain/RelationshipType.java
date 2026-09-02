package br.com.wallet.fraud.intelligence.domain;

/**
 * Types of relationships (edges) in the Fraud Intelligence Relational Graph.
 */
public enum RelationshipType {
    TRANSFERRED_TO,
    OWNS,
    USES,
    LOGGED_FROM,
    SHARES,
    SHARED_DEVICE,
    SHARED_PHONE,
    SHARED_EMAIL,
    SHARED_IP
}
