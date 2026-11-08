package com.example.demo.auth.service;
import lombok.Getter;
@Getter
public class IdentityCommandOutcome<T> {
    private final boolean accepted;
    private final T value;
    private IdentityCommandOutcome(boolean accepted,T value) { this.accepted=accepted;this.value=value; }
    public static <T> IdentityCommandOutcome<T> accepted(T value) { return new IdentityCommandOutcome<>(true,value); }
    public static <T> IdentityCommandOutcome<T> rejected(String code) { return new IdentityCommandOutcome<>(false,null); }
}
