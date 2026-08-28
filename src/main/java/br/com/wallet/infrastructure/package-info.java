@org.springframework.modulith.ApplicationModule(
    displayName = "Wallet Infrastructure Adapters",
    allowedDependencies = {"ledger::api", "ledger", "fraud::api", "fraud", "core::api", "core", "savings::api", "savings", "goals::api", "goals", "dlq::api", "dlq"}
)
package br.com.wallet.infrastructure;
