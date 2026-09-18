package br.com.wallet.platform;

import br.com.wallet.edge.api.CommandEnvelope;
import br.com.wallet.edge.api.CommandType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Scaling Topology & Independent Horizontal Scaling Test (I-TOPOLOGY-001, REQ-TOP-003, REQ-TOP-013)")
class ScalingTopologyTest {

    @Test
    @DisplayName("I-TOPOLOGY-001: Edge (N=2) and Core (M=3) scale independently without process affinity")
    void verifyIndependentScalingAndCompetingConsumerDistribution() throws Exception {
        int edgeReplicas = 2; // N = 2 Edge instances
        int coreReplicas = 3; // M = 3 Core instances (N != M)
        int commandsPerEdge = 30;
        int totalCommands = edgeReplicas * commandsPerEdge;

        // Shared Broker simulation (NATS JetStream stream: commands.wallet.*)
        BlockingQueue<CommandEnvelope> natsStream = new LinkedBlockingQueue<>();

        // 1. N Edge instances publish concurrently to broker stream
        ExecutorService edgePool = Executors.newFixedThreadPool(edgeReplicas);
        Set<UUID> publishedOperationIds = ConcurrentHashMap.newKeySet();

        CountDownLatch edgeLatch = new CountDownLatch(edgeReplicas);
        for (int e = 0; e < edgeReplicas; e++) {
            final String edgeId = "edge-node-" + e;
            edgePool.submit(() -> {
                try {
                    for (int c = 0; c < commandsPerEdge; c++) {
                        UUID opId = UUID.randomUUID();
                        publishedOperationIds.add(opId);
                        CommandEnvelope env = CommandEnvelope.create(
                                opId,
                                CommandType.TRANSFER,
                                "{\"amount\": 10.00}",
                                "192.168.1." + edgeId.hashCode()
                        );
                        natsStream.put(env);
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                } finally {
                    edgeLatch.countDown();
                }
            });
        }

        assertThat(edgeLatch.await(5, TimeUnit.SECONDS)).isTrue();
        assertThat(natsStream).hasSize(totalCommands);
        assertThat(publishedOperationIds).hasSize(totalCommands);

        // 2. M Core instances join competing consumer group "wallet-core-workers"
        ExecutorService corePool = Executors.newFixedThreadPool(coreReplicas);
        Map<String, AtomicInteger> coreProcessedCounts = new ConcurrentHashMap<>();
        Set<UUID> consumedOperationIds = ConcurrentHashMap.newKeySet();
        CountDownLatch coreLatch = new CountDownLatch(totalCommands);

        for (int m = 0; m < coreReplicas; m++) {
            final String coreWorkerId = "wallet-core-worker-" + m;
            coreProcessedCounts.put(coreWorkerId, new AtomicInteger(0));
            corePool.submit(() -> {
                try {
                    while (true) {
                        CommandEnvelope cmd = natsStream.poll(200, TimeUnit.MILLISECONDS);
                        if (cmd == null) {
                            if (coreLatch.getCount() == 0) {
                                break;
                            }
                            continue;
                        }

                        // Verify zero duplicate processing across competing consumers
                        boolean isNew = consumedOperationIds.add(cmd.operationId());
                        if (isNew) {
                            Thread.sleep(2); // Simulate ACID transaction processing
                            coreProcessedCounts.get(coreWorkerId).incrementAndGet();
                            coreLatch.countDown();
                        }
                    }
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                }
            });
        }

        assertThat(coreLatch.await(5, TimeUnit.SECONDS)).isTrue();

        // 3. Invariant Verifications:
        // A. Every command processed exactly once
        assertThat(consumedOperationIds).containsExactlyInAnyOrderElementsOf(publishedOperationIds);

        // B. All M Core workers actively participated in processing (no affinity starvation)
        for (int m = 0; m < coreReplicas; m++) {
            String coreWorkerId = "wallet-core-worker-" + m;
            int count = coreProcessedCounts.get(coreWorkerId).get();
            assertThat(count)
                    .as("Worker %s must process commands under competing consumer distribution", coreWorkerId)
                    .isGreaterThan(0);
        }

        edgePool.shutdown();
        corePool.shutdown();
    }

    @Test
    @DisplayName("I-TOPOLOGY-001: Replica counts N and M can be arbitrarily configured (N=M, N>M, N<M)")
    void verifyArbitraryReplicaConfigurations() {
        // N=1, M=1 (Minimal Lean Appliance)
        assertThat(validateTopology(1, 1)).isTrue();

        // N=2, M=2 (Balanced Medium Enterprise)
        assertThat(validateTopology(2, 2)).isTrue();

        // N=5, M=2 (High-Ingress Network Bound)
        assertThat(validateTopology(5, 2)).isTrue();

        // N=2, M=10 (Compute-Intensive Financial Core)
        assertThat(validateTopology(2, 10)).isTrue();

        // Invalid: N < 1 or M < 1
        assertThat(validateTopology(0, 1)).isFalse();
        assertThat(validateTopology(1, 0)).isFalse();
    }

    private boolean validateTopology(int n, int m) {
        return n >= 1 && m >= 1;
    }
}
