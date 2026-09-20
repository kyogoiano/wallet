package br.com.wallet.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Helm Manifest Validation Test (REQ-TOP-006, REQ-TOP-007, REQ-TOP-012, REQ-TOP-013)")
class HelmManifestValidationTest {

    private final Path helmRoot = findHelmRoot();

    private Path findHelmRoot() {
        Path p = Paths.get("deploy", "helm", "wallet-platform");
        if (Files.exists(p)) {
            return p;
        }
        p = Paths.get("..", "deploy", "helm", "wallet-platform");
        if (Files.exists(p)) {
            return p;
        }
        throw new IllegalStateException("Cannot find deploy/helm/wallet-platform directory");
    }

    @Test
    @DisplayName("REQ-TOP-006: values-harvester.yaml must configure harv-lvm-local StorageClass for direct NVMe IOPS")
    void verifyHarvesterValuesConfiguration() throws IOException {
        Path valuesFile = helmRoot.resolve("values-harvester.yaml");
        assertThat(valuesFile).exists();

        String content = Files.readString(valuesFile);
        assertThat(content).contains("storageClass: \"harv-lvm-local\"");
        assertThat(content).contains("accessMode: ReadWriteOnce");
        assertThat(content).contains("vcluster:");
    }

    @Test
    @DisplayName("REQ-TOP-007 & REQ-TOP-013: values-gke.yaml must enable HPA autoscaling for elastic cloud scale")
    void verifyGkeValuesConfiguration() throws IOException {
        Path valuesFile = helmRoot.resolve("values-gke.yaml");
        assertThat(valuesFile).exists();

        String content = Files.readString(valuesFile);
        assertThat(content).contains("storageClass: \"premium-rwo\"");
        assertThat(content).contains("enabled: true");
        assertThat(content).contains("targetConsumerLag");
    }

    @Test
    @DisplayName("REQ-TOP-001 & REQ-TOP-002: Edge and Core deployments must enforce non-root UID 10001")
    void verifyDeploymentSecurityContexts() throws IOException {
        Path edgeDeployment = helmRoot.resolve("templates/edge-deployment.yaml");
        Path coreDeployment = helmRoot.resolve("templates/core-deployment.yaml");

        assertThat(edgeDeployment).exists();
        assertThat(coreDeployment).exists();

        Path defaultValues = helmRoot.resolve("values.yaml");
        String valuesContent = Files.readString(defaultValues);

        assertThat(valuesContent).contains("runAsUser: 10001");
        assertThat(valuesContent).contains("runAsNonRoot: true");
        assertThat(valuesContent).contains("fsGroup: 10001");

        String edgeContent = Files.readString(edgeDeployment);
        assertThat(edgeContent).contains("containerPort: 8080");
        assertThat(edgeContent).contains("containerPort: 8443");
        assertThat(edgeContent).contains("mountPath: {{ .Values.edge.persistence.mountPath }}");

        String coreContent = Files.readString(coreDeployment);
        assertThat(coreContent).contains("containerPort: 8081");
        assertThat(coreContent).contains("WALLET_EDGE_ENABLED");
        assertThat(coreContent).contains("value: \"false\"");
    }

    @Test
    @DisplayName("REQ-TOP-012: NetworkPolicy must isolate Core port 8081 from public ingress")
    void verifyNetworkPolicyIsolation() throws IOException {
        Path netpol = helmRoot.resolve("templates/networkpolicy.yaml");
        assertThat(netpol).exists();

        String content = Files.readString(netpol);
        assertThat(content).contains("port: 8081");
        assertThat(content).contains("wallet-platform.coreSelectorLabels");
        assertThat(content).contains("wallet-platform.edgeSelectorLabels");
    }

    @Test
    @DisplayName("I-STORAGE-001 & I-STORAGE-002: Edge PVC must enforce ReadWriteOnce access mode")
    void verifyEdgePvcAccessMode() throws IOException {
        Path pvc = helmRoot.resolve("templates/edge-pvc.yaml");
        assertThat(pvc).exists();

        String content = Files.readString(pvc);
        assertThat(content).contains(".Values.edge.persistence.accessMode");
    }

    @Test
    @DisplayName("I-MESSAGING-001 & History 55: Core service must be internal ClusterIP management service on port 8081, not headless")
    void verifyCoreServiceIsInternalClusterIPManagement() throws IOException {
        Path coreService = helmRoot.resolve("templates/core-service.yaml");
        assertThat(coreService).exists();

        String serviceContent = Files.readString(coreService);
        assertThat(serviceContent).contains("type: {{ .Values.core.service.type }}");
        assertThat(serviceContent).contains("name: management");
        assertThat(serviceContent).contains("targetPort: 8081");
        assertThat(serviceContent).doesNotContain("clusterIP: None"); // Must NOT be headless

        Path defaultValues = helmRoot.resolve("values.yaml");
        String valuesContent = Files.readString(defaultValues);
        assertThat(valuesContent).contains("type: ClusterIP");
        assertThat(valuesContent).contains("port: 8081");
    }
}
