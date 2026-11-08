package com.example.demo.auth.config;

import com.example.demo.auth.service.IdentityCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@org.springframework.context.annotation.Profile("!test")
@Component @RequiredArgsConstructor
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class IdentityCleanupScheduler {
    private final IdentityCleanupService cleanup;
    @EventListener(ApplicationReadyEvent.class) @Order(-100)
    public void startup(){cleanup.cleanup();}
    @Scheduled(fixedDelayString="${security.verification.cleanup-delay-ms:60000}")
    public void run(){cleanup.cleanup();}
}
