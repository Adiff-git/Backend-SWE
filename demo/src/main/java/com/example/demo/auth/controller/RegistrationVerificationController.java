package com.example.demo.auth.controller;

import com.example.demo.auth.converter.AuthConverter;
import com.example.demo.auth.dto.request.ConfirmRegistrationRequest;
import com.example.demo.auth.dto.request.ResendRegistrationRequest;
import com.example.demo.auth.dto.response.PendingRegistrationResponse;
import com.example.demo.auth.dto.response.TokenResponse;
import com.example.demo.auth.service.IssuedTokens;
import com.example.demo.auth.service.PendingRegistrationService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController @RequestMapping("/api/auth/registration-verifications") @RequiredArgsConstructor
public class RegistrationVerificationController {
    private final ObjectProvider<PendingRegistrationService> registrations;
    private final AuthConverter converter;
    @PostMapping(value="/confirm",consumes="application/json")
    public ResponseEntity<TokenResponse> confirm(@Valid @RequestBody ConfirmRegistrationRequest body){
        IssuedTokens tokens=service().confirm(body.getRegistrationCredential(),UUID.fromString(body.getChallengeId()),body.getCode(),body.getPassword());
        return ResponseEntity.status(201).header("Cache-Control","no-store").header("Authorization","Bearer "+tokens.getAccessToken()).body(converter.toResponse(tokens));
    }
    @PostMapping(value="/resend",consumes="application/json")
    public ResponseEntity<PendingRegistrationResponse> resend(@Valid @RequestBody ResendRegistrationRequest body){
        return ResponseEntity.accepted().header("Cache-Control","no-store").body(service().resend(body.getRegistrationCredential(),UUID.fromString(body.getChallengeId())));
    }
    private PendingRegistrationService service(){
        PendingRegistrationService service=registrations.getIfAvailable();
        if(service==null)throw new com.example.demo.auth.exception.IdentityException(503,"REGISTRATION_UNAVAILABLE","Registration is temporarily unavailable");return service;
    }
}
