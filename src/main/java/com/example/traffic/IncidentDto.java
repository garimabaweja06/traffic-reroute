package com.example.traffic;

/** An incident as sent to the browser. */
public record IncidentDto(int edgeId, String type, String name) {
}
