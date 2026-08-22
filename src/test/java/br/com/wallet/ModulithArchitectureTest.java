package br.com.wallet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

@DisplayName("Spring Modulith Architecture Verification")
class ModulithArchitectureTest {

    private final ApplicationModules modules = ApplicationModules.of(WalletApplication.class);

    @Test
    @DisplayName("Verify that all module boundaries and package encapulations are strictly respected")
    void verifyArchitecture() {
        modules.verify();
    }

    @Test
    @DisplayName("Generate PlantUML component diagrams and documentation")
    void generateDocumentation() {
        new Documenter(modules)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
