package com.example.demo.behavior;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name="behavior.retention.enabled", havingValue="true", matchIfMissing=true)
public class BehaviorRetention {
    private final BehaviorStore store;
    private final Clock clock;
    // Each statement commits a bounded batch; bounded run time permits other scheduled jobs.
    @Scheduled(fixedDelayString="${behavior.retention.delay-ms:60000}", initialDelayString="${behavior.retention.delay-ms:60000}")
    public void cleanup() {
        var cutoff = clock.instant().minus(BehaviorService.RETENTION);
        for (int batch = 0; batch < 100; batch++) if (store.cleanup(cutoff, 1000) < 1000) break;
    }
}
