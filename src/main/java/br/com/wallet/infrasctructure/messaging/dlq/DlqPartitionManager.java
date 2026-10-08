package br.com.wallet.infrasctructure.messaging.dlq;

import br.com.wallet.infrasctructure.persistence.DlqOperationsDao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

@Component
public class DlqPartitionManager {

    private static final Logger log = LoggerFactory.getLogger(DlqPartitionManager.class);
    private final DlqOperationsDao dlqOperationsDao;
    private final Clock clock;

    public DlqPartitionManager(DlqOperationsDao dlqOperationsDao, Clock clock) {
        this.dlqOperationsDao = dlqOperationsDao;
        this.clock = clock;
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
