@org.springframework.modulith.ApplicationModule(
        displayName = "AI Financial Copilot & Proposal Domain",
        allowedDependencies = {"ledger::api", "savings::api", "goals::api", "core::api", "intelligence::api"}
)
package br.com.wallet.copilot;
