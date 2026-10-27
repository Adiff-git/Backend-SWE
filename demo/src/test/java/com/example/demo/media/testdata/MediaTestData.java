package com.example.demo.media.testdata;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

public final class MediaTestData {
    private MediaTestData() {}

    public static byte[] fixture(String name) {
        try (InputStream input = MediaTestData.class.getResourceAsStream("/media/" + name)) {
            if (input == null) throw new IllegalArgumentException("Missing fixture: " + name);
            return input.readAllBytes();
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
    }

    public static final class MutableClock extends Clock {
        private final AtomicReference<Instant> current;
        private final ZoneId zone;

        public MutableClock(Instant initial) {
            this(new AtomicReference<>(initial), ZoneOffset.UTC);
        }

        private MutableClock(AtomicReference<Instant> current, ZoneId zone) {
            this.current = current;
            this.zone = zone;
        }

        public void setInstant(Instant instant) { current.set(instant); }
        @Override public ZoneId getZone() { return zone; }
        @Override public Clock withZone(ZoneId zone) { return new MutableClock(current, zone); }
        @Override public Instant instant() { return current.get(); }
    }
}
