package com.example.demo.auth.service.impl;

import com.example.demo.auth.service.IdentityCommandOutcome;
import com.example.demo.auth.service.IdentityCommandService;
import java.util.function.Supplier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(name="security.verification.enabled", havingValue="true")
public class IdentityCommandServiceImpl implements IdentityCommandService {
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public <T> IdentityCommandOutcome<T> execute(Supplier<IdentityCommandOutcome<T>> command) {
        return command.get();
    }
}
