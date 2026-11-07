package com.example.traffic;

/** Thrown when no route exists (becomes HTTP 404). */
public class NoRouteException extends RuntimeException {
    public NoRouteException(String message) {
        super(message);
    }
}
