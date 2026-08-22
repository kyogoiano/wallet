@org.springframework.modulith.ApplicationModule(
    displayName = "Transactional Ledger & Core Banking Engine",
    allowedDependencies = {"core::api", "core", "fraud::api", "fraud"}
)
package br.com.wallet.ledger;
