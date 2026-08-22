@org.springframework.modulith.ApplicationModule(
    displayName = "Wallet Infrastructure Adapters",
    allowedDependencies = {"wallet::api", "wallet", "fraud::api", "fraud", "core::api", "core"}
)
package br.com.wallet.infrastructure;
