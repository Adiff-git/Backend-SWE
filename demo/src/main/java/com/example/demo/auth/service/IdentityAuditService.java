package com.example.demo.auth.service;

import java.util.Map;

public interface IdentityAuditService {
    enum Event { CALLBACK_ACCEPTED,CALLBACK_REJECTED,HANDOFF_ACCEPTED,HANDOFF_REJECTED,PROOF_ACCEPTED,PROOF_REJECTED,MAIL_SENT,MAIL_RETRY,MAIL_FAILED,CLEANUP_BATCH,CLEANUP_BACKLOG }
    void record(Event event);
    void afterCommit(Event event);
    Map<Event,Long> counts();
}
