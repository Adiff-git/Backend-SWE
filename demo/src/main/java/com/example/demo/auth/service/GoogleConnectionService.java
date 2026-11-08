package com.example.demo.auth.service;

import com.example.demo.auth.dto.response.GoogleConnectionResponse;

public interface GoogleConnectionService extends GoogleExchangeHandler {
    GoogleConnectionResponse getConnection(Long owner);
}
