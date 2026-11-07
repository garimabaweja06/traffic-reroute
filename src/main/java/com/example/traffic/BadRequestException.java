package com.example.traffic;

/** Thrown when the user sends something wrong (becomes HTTP 400). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
