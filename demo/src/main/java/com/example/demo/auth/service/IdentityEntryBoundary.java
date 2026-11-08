package com.example.demo.auth.service;

import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Facade errors must be raised only after the command transaction has ended. */
public final class IdentityEntryBoundary {
    private IdentityEntryBoundary(){ }
    public static void require(){
        if(TransactionSynchronizationManager.isActualTransactionActive())throw new IllegalStateException("Identity facade must not join an ambient transaction");
    }
}
