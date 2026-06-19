package com.drivers.shared.exception.ex;

public class NegativeDebtException extends RuntimeException {
    public NegativeDebtException(String message) {
        super(message);
    }
}
