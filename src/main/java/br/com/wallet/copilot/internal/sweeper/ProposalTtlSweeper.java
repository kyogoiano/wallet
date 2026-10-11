package br.com.wallet.copilot.internal.sweeper;

import br.com.wallet.copilot.internal.dao.ProposalDao;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class ProposalTtlSweeper {

    private static final Logger log = LoggerFactory.getLogger(ProposalTtlSweeper.class);

    private final ProposalDao proposalDao;

    @Autowired
    public ProposalTtlSweeper(@NonNull final ProposalDao proposalDao) {
        this.proposalDao = Objects.requireNonNull(proposalDao, "proposalDao cannot be null");
    }

    @Scheduled(fixedDelay = 60000)
    public int sweepExpiredProposals() {
        Instant now = Instant.now();
        int expiredCount = proposalDao.expireOverdueProposals(now);
        if (expiredCount > 0) {
            log.info("ProposalTtlSweeper marked {} overdue proposals as EXPIRED", expiredCount);
        }
        return expiredCount;
    }
}
