@org.springframework.modulith.ApplicationModule(
        displayName = "DLQ & Operational Recovery",
        allowedDependencies = {"ledger::api", "core::api", "core", "security::api"}
)
package br.com.wallet.dlq;

