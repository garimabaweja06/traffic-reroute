package com.example.traffic;

/**
 * One piece of road between two junctions (a "street segment").
 * It keeps its real shape (geometry) so it can be drawn with its curves.
 *
 * @param geometry points along the road as {latitude, longitude}, from junction `from` to junction `to`
 * @param oneway   true if traffic may only go from `from` to `to`
 */
public record Edge(int id, int from, int to, double lengthM, double kph, boolean oneway,
                   String name, String highway, double[][] geometry) {

    /** Travel time in minutes with no incident. */
    public double baseMinutes() {
        return lengthM / (kph / 3.6) / 60.0;
    }
}
