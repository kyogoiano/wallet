package br.com.wallet.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Platform Delivery & GitOps Workflow Verification Test (REQ-TOP-016, REQ-TOP-017, REQ-TOP-018)")
class PlatformDeliveryWorkflowTest {

    private Path resolveRoot() {
        Path root = Paths.get(".");
        if (Files.exists(root.resolve(".github"))) {
            return root;
        }
        root = Paths.get("..");
        if (Files.exists(root.resolve(".github"))) {
            return root;
        }
        throw new IllegalStateException("Cannot find workspace root directory containing .github");
    }

    @Test
    @DisplayName("REQ-TOP-016: ci-cd-appliance.yml must compile and publish multi-stage OCI images to GHCR")
    void verifyCiCdApplianceWorkflow() throws IOException {
        Path workflow = resolveRoot().resolve(".github/workflows/ci-cd-appliance.yml");
        assertThat(workflow).exists();

        String content = Files.readString(workflow);

        // Verification of triggers
        assertThat(content).contains("branches: [ main ]");
        assertThat(content).contains("tags: [ 'v*' ]");

        // Verification of GHCR registry login
        assertThat(content).contains("registry: ghcr.io");
        assertThat(content).contains("packages: write");

        // Verification of multi-stage discrete targets (I-CONTAINER-001)
        assertThat(content).contains("target: edge");
        assertThat(content).contains("target: core");
        assertThat(content).contains("wallet-edge");
        assertThat(content).contains("wallet-core");

        // Verification of Buildx and GitHub Actions cache
        assertThat(content).contains("docker/setup-buildx-action");
        assertThat(content).contains("cache-from: type=gha");
    }

    @Test
    @DisplayName("REQ-TOP-017: ci-cd-appliance.yml must package and publish Helm chart as OCI artifact to GHCR")
    void verifyHelmOciWorkflow() throws IOException {
        Path workflow = resolveRoot().resolve(".github/workflows/ci-cd-appliance.yml");
        assertThat(workflow).exists();

        // Assert legacy GitHub Pages workflow is eliminated in favor of GHCR OCI
        Path legacyPagesWorkflow = resolveRoot().resolve(".github/workflows/helm-pages.yml");
        assertThat(legacyPagesWorkflow).doesNotExist();

        String content = Files.readString(workflow);

        // Verification of Helm OCI packaging and pushing to GHCR
        assertThat(content).contains("helm package deploy/helm/wallet-platform");
        assertThat(content).contains("helm push");
        assertThat(content).contains("oci://ghcr.io");
        assertThat(content).contains("helm registry login ghcr.io");

        // Verification of chart descriptor metadata
        Path chartYaml = resolveRoot().resolve("deploy/helm/wallet-platform/Chart.yaml");
        assertThat(chartYaml).exists();
        String chartContent = Files.readString(chartYaml);
        assertThat(chartContent).contains("name: wallet-platform");
        assertThat(chartContent).contains("version: 0.1.0");
        assertThat(chartContent).contains("apiVersion: v2");
    }

    @Test
    @DisplayName("REQ-TOP-018: Portainer GitOps redeployment webhook must be integrated in CI/CD pipeline")
    void verifyPortainerGitOpsIntegration() throws IOException {
        Path workflow = resolveRoot().resolve(".github/workflows/ci-cd-appliance.yml");
        assertThat(workflow).exists();

        String content = Files.readString(workflow);

        // Verification of Portainer webhook trigger step
        assertThat(content).contains("redeploy-appliance:");
        assertThat(content).contains("PORTAINER_WEBHOOK_URL: ${{ secrets.PORTAINER_WEBHOOK_URL }}");
        assertThat(content).contains("curl");

        // Verification of Plan B appliance compose matching Portainer socket requirements
        Path applianceCompose = resolveRoot().resolve("docker-compose.appliance.yaml");
        assertThat(applianceCompose).exists();
        String composeContent = Files.readString(applianceCompose);
        assertThat(composeContent).contains("image: portainer/portainer-ce:latest");
        assertThat(composeContent).contains("/var/run/docker.sock:/var/run/docker.sock");
    }
}
