package com.example.traffic;

/** Kinds of incidents. The number is how many times slower the road becomes. */
public enum IncidentType {
    CONGESTION(2.0),
    ACCIDENT(3.0),
    ROAD_BLOCK(Double.POSITIVE_INFINITY);

    public final double slowdown;

    IncidentType(double slowdown) {
        this.slowdown = slowdown;
    }
}
