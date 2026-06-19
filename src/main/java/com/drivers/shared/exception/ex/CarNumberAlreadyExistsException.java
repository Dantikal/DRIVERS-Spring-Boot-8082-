package com.drivers.shared.exception.ex;

public class CarNumberAlreadyExistsException extends RuntimeException {
    public CarNumberAlreadyExistsException(String message) {
        super(message);
    }
}
