package com.example.demo.auth.config;

import com.example.demo.auth.service.VerificationMailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.event.EventListener;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name="security.verification.enabled",havingValue="true")
public class VerificationMailWorker {
    private final VerificationMailService mail;
    private final boolean enabled;
    public VerificationMailWorker(VerificationMailService mail,@Value("${security.verification.mail-worker-enabled:true}") boolean enabled){this.mail=mail;this.enabled=enabled;}
    @EventListener(ApplicationReadyEvent.class)
    public void startup(){run();}
    @Scheduled(fixedDelayString="${security.verification.mail-delay-ms:1000}")
    public void run(){if(enabled)mail.dispatch();}
}
