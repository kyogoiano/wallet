package br.com.wallet.platform;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Container Packaging & Boundary Verification Test (I-CONTAINER-001, REQ-TOP-001, REQ-TOP-002)")
class ContainerImageVerificationTest {

    private final JavaClasses edgeClasses = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("br.com.wallet.edge");

    @Test
    @DisplayName("REQ-TOP-001 & I-CONTAINER-001: Edge module must contain zero references to JDBC, JPA, or PostgreSQL")
    void edgeClasspathMustExcludeRelationalPersistence() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("br.com.wallet.edge..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "javax.sql..",
                        "jakarta.persistence..",
                        "org.springframework.jdbc..",
                        "org.springframework.data.jpa..",
                        "org.hibernate..",
                        "org.postgresql..",
                        "com.zaxxer.hikari..",
                        "org.flywaydb.."
                );

        rule.check(edgeClasses);
    }

    @Test
    @DisplayName("REQ-TOP-001: edge/build.gradle must not declare relational database dependencies")
    void edgeBuildGradleMustNotContainDatabaseStarters() throws IOException {
        Path edgeBuildGradle = Paths.get("edge", "build.gradle");
        if (!Files.exists(edgeBuildGradle)) {
            edgeBuildGradle = Paths.get("..", "edge", "build.gradle");
        }
        assertThat(edgeBuildGradle).exists();

        List<String> lines = Files.readAllLines(edgeBuildGradle);
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("//") || trimmed.isEmpty()) {
                continue;
            }
            assertThat(trimmed)
                    .doesNotContain("org.postgresql")
                    .doesNotContain("spring-boot-starter-data-jpa")
                    .doesNotContain("spring-boot-starter-jdbc")
                    .doesNotContain("flyway-core")
                    .doesNotContain("HikariCP");
        }
    }

    @Test
    @DisplayName("REQ-TOP-001, REQ-TOP-002 & I-CONTAINER-001: Dockerfile must enforce non-root UID 10001 for both Edge and Core")
    void dockerfileMustEnforceNonRootExecution() throws IOException {
        Path dockerfilePath = Paths.get("dockerfile");
        if (!Files.exists(dockerfilePath)) {
            dockerfilePath = Paths.get("..", "dockerfile");
        }
        assertThat(dockerfilePath).exists();

        String content = Files.readString(dockerfilePath);

        // Edge Stage Hardening Checks
        assertThat(content).contains("FROM oraclelinux:9-slim AS edge");
        assertThat(content).contains("useradd -u 10001");
        assertThat(content).contains("chown -R 10001:10001 /spool");
        assertThat(content).contains("chmod 700 /spool");
        assertThat(content).contains("USER 10001:10001");

        // Core Stage Hardening Checks
        assertThat(content).contains("FROM oraclelinux:9-slim AS core");
        assertThat(content).contains("libstdc++ libgomp curl");
        assertThat(content).contains("USER 10001:10001");

        // JVM Safety Options
        assertThat(content).contains("-Duser.timezone=UTC");
        assertThat(content).contains("-XX:MaxRAMPercentage=75");
    }
}
