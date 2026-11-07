package com.example.traffic;

import java.util.List;

/** The whole road map, sent once so the page can draw it. */
public record NetworkResponse(String source, double[] bounds, int junctions, List<EdgeDto> edges) {

    /** coords are {latitude, longitude} points along the road. */
    public record EdgeDto(int id, String name, String highway, double lengthM, double minutes,
                          boolean oneway, double[][] coords) {
    }
}
