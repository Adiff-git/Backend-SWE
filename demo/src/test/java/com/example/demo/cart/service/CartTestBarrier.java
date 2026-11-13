package com.example.demo.cart.service;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class CartTestBarrier {
    private CartTestBarrier() { }
    public static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Barrier timed out"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
