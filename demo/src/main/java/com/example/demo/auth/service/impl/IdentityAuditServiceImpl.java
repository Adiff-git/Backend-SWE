package com.example.demo.auth.service.impl;

import com.example.demo.auth.service.IdentityAuditService;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class IdentityAuditServiceImpl implements IdentityAuditService {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(IdentityAuditServiceImpl.class);
    private final Map<Event,LongAdder> counters=new ConcurrentHashMap<>();
    public void record(Event event){
        LongAdder counter=counters.computeIfAbsent(event,ignored->new LongAdder());counter.increment();
        LOG.info("Identity outcome {} count={}",event,counter.sum());
    }
    public void afterCommit(Event event){
        if(!TransactionSynchronizationManager.isSynchronizationActive())throw new IllegalStateException("Audit commit requires transaction synchronization");
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization(){@Override public void afterCommit(){record(event);}});
    }
    public Map<Event,Long> counts(){Map<Event,Long> snapshot=new EnumMap<>(Event.class);counters.forEach((event,count)->snapshot.put(event,count.sum()));return Map.copyOf(snapshot);}
}
