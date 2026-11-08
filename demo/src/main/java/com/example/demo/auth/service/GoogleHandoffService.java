package com.example.demo.auth.service;

import com.example.demo.auth.entity.OAuthHandoff;
import java.util.UUID;

public interface GoogleHandoffService {
    OAuthHandoff getHint(UUID id);
    OAuthHandoff lockForRedemption(UUID id,byte[] hash,String verifier);
    void consume(OAuthHandoff handoff);
}
