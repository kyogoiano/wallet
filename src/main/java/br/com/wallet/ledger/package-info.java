@org.springframework.modulith.ApplicationModule(
    displayName = "Financial Core & Transactional Ledger",
    allowedDependencies = {"core::api", "core", "fraud::api", "fraud::fusion-api", "fraud"}
)
package br.com.wallet.ledger;
