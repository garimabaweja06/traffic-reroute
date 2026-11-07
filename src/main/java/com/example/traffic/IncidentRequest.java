package com.example.traffic;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

/**
 * Report an incident in ONE of two ways:
 *   {"edgeId": 120, "type": "ROAD_BLOCK"}                       (the map page uses this)
 *   {"lat": 30.3401, "lon": 76.8290, "type": "ACCIDENT"}        (like a phone sending its GPS position)
 */
public record IncidentRequest(
        Integer edgeId,
        @DecimalMin("-90") @DecimalMax("90") Double lat,
        @DecimalMin("-180") @DecimalMax("180") Double lon,
        @NotNull IncidentType type) {
}
