package com.example.traffic;

import java.util.List;

/**
 * A route: where to drive, how long, how far.
 * coordinates = every {latitude, longitude} point along the way (follows the real road curves).
 * start / end = where your points were snapped onto the road network.
 */
public record RouteResponse(double minutes, double distanceMeters, double[][] coordinates,
                            List<Integer> edgeIds, List<String> streets, double[] start, double[] end) {
}
