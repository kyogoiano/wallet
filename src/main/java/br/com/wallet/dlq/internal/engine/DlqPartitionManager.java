package br.com.wallet.dlq.internal.engine;

import br.com.wallet.dlq.internal.persistence.DlqOperationsDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Component
public class DlqPartitionManager {

    private static final Logger log = LoggerFactory.getLogger(DlqPartitionManager.class);
    private final DlqOperationsDao dlqOperationsDao;
    private final Clock clock;

    public DlqPartitionManager(@NonNull final DlqOperationsDao dlqOperationsDao, @NonNull final Clock clock) {
        this.dlqOperationsDao = Objects.requireNonNull(dlqOperationsDao, "dlqOperationsDao cannot be null");
        this.clock = Objects.requireNonNull(clock, "clock cannot be null");
    }

    // runs weekly
    @Scheduled(cron = "0 0 0 * * MON")
    public void ensurePartitions() {
        log.info("Ensuring dlq operations partitions...");

        final var now = clock.instant();

        for (int i = 0; i < 7; i++) {
            dlqOperationsDao.createPartition(now.plus(i, ChronoUnit.DAYS));
        }

        log.info("Dlq operations partitions created! at={}", now.atOffset(ZoneOffset.UTC));

        var droppedPartitions = dlqOperationsDao.cleanUpWeekly(now);

        log.info("Dlq operations partition dropped! count={}, at={}", droppedPartitions, now.atOffset(ZoneOffset.UTC));
    }
}
