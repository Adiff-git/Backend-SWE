package com.example.demo.auth.config;
import com.example.demo.auth.service.AuthSessionService;
import com.example.demo.auth.service.AuthTransactionService;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import lombok.RequiredArgsConstructor;
@Component @RequiredArgsConstructor @ConditionalOnProperty(name="security.auth.cleanup.enabled",havingValue="true",matchIfMissing=true)
public class AuthCleanupScheduler {
    private final AuthSessionService sessions;
    private final AuthTransactionService commands;
    private final Clock clock;
    @Scheduled(cron="0 0 2 * * *",zone="UTC") public void cleanup() {
        Instant deadline=clock.instant();
        List<Long> owners;
        do {
            owners=sessions.findExpiredOwners(deadline,100);
            for(Long owner:owners)commands.cleanupOwner(owner,deadline);
        }
        while(!owners.isEmpty());
    }
}
