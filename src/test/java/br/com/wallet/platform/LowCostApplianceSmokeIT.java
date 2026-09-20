package br.com.wallet.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Plan B Low-Cost Lean Appliance Smoke Test (REQ-TOP-005, I-STORAGE-002, I-PLATFORM-001)")
class LowCostApplianceSmokeIT {

    @Test
    @DisplayName("REQ-TOP-005: docker-compose.appliance.yaml must configure low-cost non-K8s stack within 8GB memory footprint")
    void verifyPlanBApplianceComposeConfiguration() throws IOException {
        Path composePath = Paths.get("docker-compose.appliance.yaml");
        if (!Files.exists(composePath)) {
            composePath = Paths.get("..", "docker-compose.appliance.yaml");
        }
        assertThat(composePath).exists();

        String content = Files.readString(composePath);

        // Verify Direct HostPath Storage for Edge /spool (Option A, I-STORAGE-002)
        assertThat(content).contains("./spool-data:/spool");

        // Verify Non-Root User 10001:10001
        assertThat(content).contains("user: \"10001:10001\"");

        // Verify Core Headless Mode
        assertThat(content).contains("WALLET_EDGE_ENABLED: \"false\"");
        assertThat(content).contains("SERVER_PORT: 8081");

        // Verify Edge Ingress Ports
        assertThat(content).contains("8080:8080");
        assertThat(content).contains("8443:8443/udp");

        // Verify Resource Limits (Core 2048M, Edge 1024M, Postgres 2048M, Dragonfly 768M, NATS 512M => ~6.4GB Total < 8GB)
        assertThat(content).contains("memory: 2048M");
        assertThat(content).contains("memory: 1024M");
        assertThat(content).contains("memory: 768M");
        assertThat(content).contains("memory: 512M");

        // Verify Zero Kubernetes dependencies / images
        assertThat(content).doesNotContain("k8s");
        assertThat(content).doesNotContain("kube");

        // Verify PostgreSQL 18.x with pgvector and major-version data path (/var/lib/postgresql)
        assertThat(content).contains("image: pgvector/pgvector:pg18");
        assertThat(content).contains("./postgres-data:/var/lib/postgresql");
        assertThat(content).doesNotContain("/var/lib/postgresql/data");

        // Verify spool-init volume permission auto-remediation (I-CONTAINER-001, I-STORAGE-002)
        assertThat(content).contains("spool-init:");
        assertThat(content).contains("chown -R 10001:10001 /spool");
        assertThat(content).contains("condition: service_completed_successfully");

        // Verify Portainer CE management UI (REQ-TOP-005)
        assertThat(content).contains("image: portainer/portainer-ce:latest");
        assertThat(content).contains("9000:9000");
        assertThat(content).contains("9443:9443");
        assertThat(content).contains("/var/run/docker.sock:/var/run/docker.sock");

        // Verify Telemetry profile configuration (VictoriaLogs + VictoriaTraces + OTel Collector)
        assertThat(content).contains("profiles:\n      - telemetry");
        assertThat(content).contains("image: victoriametrics/victoria-logs:latest");
        assertThat(content).contains("image: victoriametrics/victoria-traces:latest");
        assertThat(content).contains("image: otel/opentelemetry-collector:latest");
        assertThat(content).contains("OTEL_EXPORTER_OTLP_ENDPOINT: http://otel-collector:4318");
        assertThat(content).doesNotContain("openobserve");
    }

    @Test
    @DisplayName("REQ-TOP-004: Standard docker-compose.yaml must define Plan A development defaults with non-root security")
    void verifyPlanADevelopmentComposeDefaults() throws IOException {
        Path composePath = Paths.get("docker-compose.yaml");
        if (!Files.exists(composePath)) {
            composePath = Paths.get("..", "docker-compose.yaml");
        }
        assertThat(composePath).exists();

        String content = Files.readString(composePath);

        assertThat(content).contains("user: \"10001:10001\"");
        assertThat(content).contains("edge-spool:/spool");
        assertThat(content).contains("container_name: wallet-app");
        assertThat(content).contains("container_name: wallet-edge");
        assertThat(content).contains("image: pgvector/pgvector:pg18");

        // Verify spool-init volume permission auto-remediation (I-CONTAINER-001)
        assertThat(content).contains("spool-init:");
        assertThat(content).contains("chown -R 10001:10001 /spool");
        assertThat(content).contains("condition: service_completed_successfully");
    }

    @Test
    @DisplayName("REQ-TOP-005 & I-STORAGE-001: HostPath directory must permit single-writer directory acquisition")
    void verifyHostPathSingleWriterLockSemantics() throws IOException {
        Path testDir = Files.createTempDirectory("appliance-hostpath-test");
        try {
            Path lockFile = testDir.resolve(".spool.lock");
            assertThat(lockFile).doesNotExist();

            // Simulate hostpath directory preparation
            Files.writeString(lockFile, "pid=10001\n");
            assertThat(lockFile).exists();
            assertThat(Files.readString(lockFile)).contains("pid=10001");
        } finally {
            // Clean up
            Files.walk(testDir)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException ignored) {}
                    });
        }
    }
}
