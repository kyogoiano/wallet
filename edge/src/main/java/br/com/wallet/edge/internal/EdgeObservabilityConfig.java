package br.com.wallet.edge.internal;

import br.com.wallet.edge.internal.journal.segmented.SegmentedFileJournal;
import br.com.wallet.edge.internal.resilience.IngressBulkhead;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 14 Micrometer SLO and operational metrics for Reactive Edge Gateway (PLAN-000.9 Section 6).
 */
@Configuration
public class EdgeObservabilityConfig {

    private final Counter ingressRequestsTotal;
    private final Counter rateLimitRejectionsTotal;
    private final Counter bulkheadRejectionsTotal;
    private final Counter replaySuccessTotal;
    private final Counter corruptionIncidentsTotal;

    private final Timer natsPublishTimer;
    private final Timer journalAppendTimer;
    private final Timer journalFsyncTimer;

    private final AtomicLong recoveryBacklogRecords = new AtomicLong(0);

    public EdgeObservabilityConfig(
            MeterRegistry registry,
            IngressBulkhead bulkhead,
            SegmentedFileJournal journal
    ) {
        // Counters
        this.ingressRequestsTotal = Counter.builder("edge_ingress_requests_total")
                .description("Total incoming financial command requests at the perimeter")
                .register(registry);

        this.rateLimitRejectionsTotal = Counter.builder("edge_rate_limit_rejections_total")
                .description("Total perimeter rate limit rejections (HTTP 429)")
                .register(registry);

        this.bulkheadRejectionsTotal = Counter.builder("edge_bulkhead_rejections_total")
                .description("Total bulkhead capacity rejections (HTTP 429)")
                .register(registry);

        this.replaySuccessTotal = Counter.builder("edge_replay_success_total")
                .description("Total spooled records successfully replayed to broker")
                .register(registry);

        this.corruptionIncidentsTotal = Counter.builder("edge_journal_corruption_incidents_total")
                .description("Total disk CRC corruption incidents isolated")
                .register(registry);

        // Timers
        this.natsPublishTimer = Timer.builder("edge_nats_publish_latency")
                .description("Latency of primary NATS JetStream command publication")
                .register(registry);

        this.journalAppendTimer = Timer.builder("edge_journal_append_latency")
                .description("Latency of local journal record append")
                .register(registry);

        this.journalFsyncTimer = Timer.builder("edge_journal_fsync_latency")
                .description("Latency of group commit FileChannel force(false) operations")
                .register(registry);

        // Gauges
        Gauge.builder("edge_inflight_commands", bulkhead, IngressBulkhead::getCurrentInflight)
                .description("Current number of active inflight requests inside the edge bulkhead")
                .register(registry);

        Gauge.builder("edge_journal_usage_bytes", journal, SegmentedFileJournal::currentSpoolUsageBytes)
                .description("Total disk storage utilized by spool journal files in bytes")
                .register(registry);

        Gauge.builder("edge_journal_usage_percent", journal, SegmentedFileJournal::spoolUsagePercent)
                .description("Spool journal capacity utilization percentage")
                .register(registry);

        Gauge.builder("edge_recovery_backlog_records", recoveryBacklogRecords, AtomicLong::get)
                .description("Number of pending journal records queued for crash recovery replay")
                .register(registry);
    }

    public Counter getIngressRequestsTotal() { return ingressRequestsTotal; }
    public Counter getRateLimitRejectionsTotal() { return rateLimitRejectionsTotal; }
    public Counter getBulkheadRejectionsTotal() { return bulkheadRejectionsTotal; }
    public Counter getReplaySuccessTotal() { return replaySuccessTotal; }
    public Counter getCorruptionIncidentsTotal() { return corruptionIncidentsTotal; }
    public Timer getNatsPublishTimer() { return natsPublishTimer; }
    public Timer getJournalAppendTimer() { return journalAppendTimer; }
    public Timer getJournalFsyncTimer() { return journalFsyncTimer; }
    public AtomicLong getRecoveryBacklogRecords() { return recoveryBacklogRecords; }
}
