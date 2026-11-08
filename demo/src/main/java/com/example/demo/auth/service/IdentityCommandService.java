package com.example.demo.auth.service;

import java.util.function.Supplier;

public interface IdentityCommandService {
    <T> IdentityCommandOutcome<T> execute(Supplier<IdentityCommandOutcome<T>> command);
}
