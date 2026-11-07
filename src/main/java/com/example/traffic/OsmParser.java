package com.example.traffic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Turns OpenStreetMap data (the JSON from the Overpass API) into a {@link RoadNetwork}.
 *
 * The data contains NODES (points with coordinates) and WAYS (a list of nodes plus tags such as
 * highway=residential, name=..., oneway=yes). A way may have hundreds of points but only a few
 * real junctions, so we:
 *   1. find the junctions (a node used by two ways, or the end of a way),
 *   2. cut every way into street segments between junctions (each keeps its curved shape),
 *   3. keep only the largest connected piece, so every junction can reach every other.
 *
 * It works on plain Java maps and lists, so it has no library dependency and is easy to test.
 */
public final class OsmParser {

    /** Road types we drive on. Footpaths, cycleways, etc. are ignored. */
    private static final Set<String> ROAD_TYPES = Set.of(
            "motorway", "trunk", "primary", "secondary", "tertiary", "unclassified", "residential",
            "living_street", "motorway_link", "trunk_link", "primary_link", "secondary_link", "tertiary_link");

    /** Typical city speed in km/h when the map has no maxspeed tag. */
    private static final Map<String, Double> DEFAULT_KPH = Map.ofEntries(
            Map.entry("motorway", 60.0), Map.entry("trunk", 50.0), Map.entry("primary", 40.0),
            Map.entry("secondary", 35.0), Map.entry("tertiary", 30.0), Map.entry("unclassified", 25.0),
            Map.entry("residential", 25.0), Map.entry("living_street", 15.0));
    private static final double FALLBACK_KPH = 25.0;

    private record RawWay(long[] nodes, Map<String, String> tags) {
    }

    private record RawEdge(int from, int to, double lengthM, double kph, boolean oneway,
                           String name, String highway, double[][] geometry) {
    }

    private OsmParser() {
    }

    public static RoadNetwork parse(Map<String, Object> root, String source) {
        List<?> elements = (List<?>) root.get("elements");
        if (elements == null) {
            throw new IllegalArgumentException("Not OpenStreetMap data: no 'elements' list");
        }

        // Pass 1: read every node's coordinates and every drivable way.
        Map<Long, double[]> coords = new HashMap<>();
        List<RawWay> ways = new ArrayList<>();
        for (Object o : elements) {
            Map<?, ?> el = (Map<?, ?>) o;
            Object type = el.get("type");
            if ("node".equals(type)) {
                coords.put(((Number) el.get("id")).longValue(),
                        new double[]{((Number) el.get("lat")).doubleValue(), ((Number) el.get("lon")).doubleValue()});
            } else if ("way".equals(type) && el.get("tags") instanceof Map<?, ?> rawTags) {
                Map<String, String> tags = new HashMap<>();
                rawTags.forEach((k, v) -> tags.put(String.valueOf(k), String.valueOf(v)));
                if (ROAD_TYPES.contains(tags.get("highway")) && el.get("nodes") instanceof List<?> nodeList) {
                    long[] ids = nodeList.stream().mapToLong(x -> ((Number) x).longValue()).toArray();
                    ways.add(new RawWay(ids, tags));
                }
            }
        }

        // Keep only nodes we have coordinates for, and drop repeated neighbours.
        List<long[]> cleaned = new ArrayList<>();
        for (RawWay w : ways) {
            long[] ids = java.util.Arrays.stream(w.nodes()).filter(coords::containsKey).toArray();
            cleaned.add(dedupe(ids));
        }

        // Pass 2: count how many times each node is used. Used twice = where two roads meet.
        Map<Long, Integer> uses = new HashMap<>();
        for (long[] ids : cleaned) {
            for (long id : ids) {
                uses.merge(id, 1, Integer::sum);
            }
        }

        // Pass 3: cut every way into street segments between junctions.
        Map<Long, Integer> vertexOf = new LinkedHashMap<>();
        List<RawEdge> raw = new ArrayList<>();
        for (int w = 0; w < ways.size(); w++) {
            long[] ids = cleaned.get(w);
            Map<String, String> tags = ways.get(w).tags();
            int segStart = 0;
            for (int i = 1; i < ids.length; i++) {
                boolean junction = i == ids.length - 1 || uses.get(ids[i]) > 1;
                if (!junction) {
                    continue;
                }
                RawEdge e = makeEdge(ids, segStart, i, coords, vertexOf, tags);
                if (e != null) {
                    raw.add(e);
                }
                segStart = i;
            }
        }

        return buildNetwork(source, vertexOf, coords, raw);
    }

    private static RawEdge makeEdge(long[] ids, int from, int to, Map<Long, double[]> coords,
                                    Map<Long, Integer> vertexOf, Map<String, String> tags) {
        double[][] geometry = new double[to - from + 1][];
        double length = 0;
        for (int i = from; i <= to; i++) {
            geometry[i - from] = coords.get(ids[i]);
            if (i > from) {
                double[] a = geometry[i - from - 1], b = geometry[i - from];
                length += Geo.meters(a[0], a[1], b[0], b[1]);
            }
        }
        if (length < 1) {
            return null;                                       // zero-length piece: ignore
        }
        int a = vertexOf.computeIfAbsent(ids[from], k -> vertexOf.size());
        int b = vertexOf.computeIfAbsent(ids[to], k -> vertexOf.size());

        String oneway = tags.get("oneway");
        boolean reversed = "-1".equals(oneway) || "reverse".equals(oneway);
        boolean forwardOnly = "yes".equals(oneway) || "true".equals(oneway) || "1".equals(oneway)
                || "roundabout".equals(tags.get("junction")) || reversed;
        if (reversed) {                                        // drawn against the allowed direction: flip it
            int tmp = a;
            a = b;
            b = tmp;
            geometry = reverse(geometry);
        }

        String highway = tags.get("highway");
        double kph = parseSpeed(tags.get("maxspeed"), DEFAULT_KPH.getOrDefault(highway, FALLBACK_KPH));
        return new RawEdge(a, b, length, kph, forwardOnly, tags.get("name"), highway, geometry);
    }

    private static RoadNetwork buildNetwork(String source, Map<Long, Integer> vertexOf,
                                            Map<Long, double[]> coords, List<RawEdge> raw) {
        int n = vertexOf.size();
        if (n == 0 || raw.isEmpty()) {
            throw new IllegalArgumentException("No drivable roads found in the data");
        }

        // Find the largest connected piece with union-find (merge junctions joined by a road).
        int[] parent = new int[n];
        for (int i = 0; i < n; i++) {
            parent[i] = i;
        }
        for (RawEdge e : raw) {
            parent[find(parent, e.from())] = find(parent, e.to());
        }
        int[] size = new int[n];
        for (int i = 0; i < n; i++) {
            size[find(parent, i)]++;
        }
        int biggest = 0;
        for (int i = 1; i < n; i++) {
            if (size[i] > size[biggest]) {
                biggest = i;
            }
        }

        // Re-number the junctions of the biggest piece 0..m-1.
        int[] newIndex = new int[n];
        java.util.Arrays.fill(newIndex, -1);
        List<double[]> points = new ArrayList<>();
        for (Map.Entry<Long, Integer> en : vertexOf.entrySet()) {
            int v = en.getValue();
            if (find(parent, v) == find(parent, biggest)) {
                newIndex[v] = points.size();
                points.add(coords.get(en.getKey()));
            }
        }
        double[] lat = new double[points.size()];
        double[] lon = new double[points.size()];
        for (int i = 0; i < points.size(); i++) {
            lat[i] = points.get(i)[0];
            lon[i] = points.get(i)[1];
        }

        List<Edge> edges = new ArrayList<>();
        for (RawEdge e : raw) {
            if (newIndex[e.from()] < 0) {
                continue;                                      // belongs to a disconnected piece
            }
            int id = edges.size();
            edges.add(new Edge(id, newIndex[e.from()], newIndex[e.to()], e.lengthM(), e.kph(), e.oneway(),
                    e.name(), e.highway(), e.geometry()));
        }
        return new RoadNetwork(source, lat, lon, edges);
    }

    private static int find(int[] parent, int x) {
        while (parent[x] != x) {
            parent[x] = parent[parent[x]];                     // shorten the chain as we go
            x = parent[x];
        }
        return x;
    }

    private static long[] dedupe(long[] ids) {
        long[] out = new long[ids.length];
        int k = 0;
        for (long id : ids) {
            if (k == 0 || out[k - 1] != id) {
                out[k++] = id;
            }
        }
        return java.util.Arrays.copyOf(out, k);
    }

    private static double[][] reverse(double[][] g) {
        double[][] r = new double[g.length][];
        for (int i = 0; i < g.length; i++) {
            r[i] = g[g.length - 1 - i];
        }
        return r;
    }

    /** "50" or "50 km/h" gives 50. Anything we do not understand gives the default. */
    private static double parseSpeed(String maxspeed, double fallback) {
        if (maxspeed == null) {
            return fallback;
        }
        StringBuilder digits = new StringBuilder();
        for (char c : maxspeed.toCharArray()) {
            if (Character.isDigit(c)) {
                digits.append(c);
            } else if (digits.length() > 0) {
                break;
            }
        }
        if (digits.length() == 0) {
            return fallback;
        }
        double v = Double.parseDouble(digits.toString());
        return v >= 5 && v <= 130 ? v : fallback;
    }
}
