package com.example.demo.behavior;
import com.example.demo.common.exception.ApplicationException;
public class BehaviorException extends ApplicationException {
    public BehaviorException(int status, String message) { super(status, message); }
}
