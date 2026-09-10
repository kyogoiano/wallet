@org.springframework.modulith.ApplicationModule(
    displayName = "Wallet Infrastructure Adapters",
    allowedDependencies = {"ledger::api", "fraud::api", "fraud", "core::api", "core", "savings::api", "savings", "goals::api", "goals", "dlq::api", "dlq", "fraud::investigation-api", "fraud::investigation-spi", "fraud::embeddings-api", "fraud::embeddings-spi", "fraud::fusion-api", "edge::api"}
)
package br.com.wallet.infrastructure;
