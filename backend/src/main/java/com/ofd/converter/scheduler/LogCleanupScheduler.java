package com.ofd.converter.scheduler;

import com.ofd.converter.repository.OperationLogRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LogCleanupScheduler {

    private final OperationLogRepository repo;
    private final int retentionDays;

    public LogCleanupScheduler(OperationLogRepository repo,
                               @Value("${log.retention-days:90}") int retentionDays) {
        this.repo = repo;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedRate = 86400_000L)
    public void cleanup() {
        long cutoff = System.currentTimeMillis() - (long) retentionDays * 86400_000L;
        repo.deleteByCreatedAtBefore(cutoff);
    }
}
