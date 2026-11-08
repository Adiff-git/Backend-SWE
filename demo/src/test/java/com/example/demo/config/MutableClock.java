package com.example.demo.config;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MutableClock extends Clock {
    private final AtomicReference<Instant> now=new AtomicReference<>(Instant.now());
    public void set(Instant value){now.set(value);}
    public ZoneId getZone(){return ZoneOffset.UTC;}
    public Clock withZone(ZoneId zone){return Clock.fixed(now.get(),zone);}
    public Instant instant(){return now.get();}
}
