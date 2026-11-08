package com.example.demo.auth.service;

import java.util.UUID;

public interface InitialPasswordService {
    void set(Long owner,UUID sid,String password,String proof);
}
