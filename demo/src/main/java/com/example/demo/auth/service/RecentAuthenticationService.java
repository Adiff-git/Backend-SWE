package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.RecentAuthProofResponse;
import com.example.demo.auth.entity.RecentAuthProof;
import java.util.UUID;

public interface RecentAuthenticationService {
    RecentAuthProofResponse password(Long owner,UUID sid,RecentAuthProof.Operation operation,String password);
    RecentAuthProof lockProof(Long owner,UUID sid,RecentAuthProof.Operation operation,String secret);
    RecentAuthProofResponse google(Long owner,UUID sid,RecentAuthProof.Operation operation,Long identity,java.time.Instant authenticatedAt);
    void consume(RecentAuthProof proof);
}
