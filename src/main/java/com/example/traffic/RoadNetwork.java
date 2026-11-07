package com.example.traffic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The road map as a graph: junctions are nodes, street segments are edges.
 * The roads never change after loading. Only the incidents change, and they live in a
 * ConcurrentHashMap because a web server handles many requests at the same time.
 */
public class RoadNetwork {

    /** A way to leave a junction: along `edge`, arriving at node `to`. `forward` says which way we travel. */
    public record Arc(Edge edge, int to, boolean forward) {
    }

    public record NodeHit(int node, double meters) {
    }

    public record EdgeHit(Edge edge, double meters) {
    }

    private final String source;
    private final double[] nodeLat;
    private final double[] nodeLon;
    private final List<Edge> edges;
    private final List<List<Arc>> arcs;
    private final Map<Integer, IncidentType> incidents = new ConcurrentHashMap<>();

    public RoadNetwork(String source, double[] nodeLat, double[] nodeLon, List<Edge> edges) {
        this.source = source;
        this.nodeLat = nodeLat;
        this.nodeLon = nodeLon;
        this.edges = List.copyOf(edges);
        this.arcs = new ArrayList<>(nodeLat.length);
        for (int i = 0; i < nodeLat.length; i++) {
            arcs.add(new ArrayList<>());
        }
        for (Edge e : edges) {
            arcs.get(e.from()).add(new Arc(e, e.to(), true));
            if (!e.oneway()) {
                arcs.get(e.to()).add(new Arc(e, e.from(), false));   // two-way road: also usable backwards
            }
        }
    }

    // ---- the map -------------------------------------------------------------------------

    public String source() {
        return source;
    }

    public int nodeCount() {
        return nodeLat.length;
    }

    public List<Edge> edges() {
        return edges;
    }

    public Edge edge(int id) {
        return id >= 0 && id < edges.size() ? edges.get(id) : null;
    }

    public List<Arc> arcsFrom(int node) {
        return arcs.get(node);
    }

    public double lat(int node) {
        return nodeLat[node];
    }

    public double lon(int node) {
        return nodeLon[node];
    }

    /** {south, west, north, east} of all junctions. */
    public double[] bounds() {
        double s = Double.MAX_VALUE, w = Double.MAX_VALUE, n = -Double.MAX_VALUE, e = -Double.MAX_VALUE;
        for (int i = 0; i < nodeLat.length; i++) {
            s = Math.min(s, nodeLat[i]);
            n = Math.max(n, nodeLat[i]);
            w = Math.min(w, nodeLon[i]);
            e = Math.max(e, nodeLon[i]);
        }
        return new double[]{s, w, n, e};
    }

    // ---- incidents -----------------------------------------------------------------------

    public void reportIncident(int edgeId, IncidentType type) {
        incidents.put(edgeId, type);
    }

    public void clearIncident(int edgeId) {
        incidents.remove(edgeId);
    }

    public void clearAllIncidents() {
        incidents.clear();
    }

    public IncidentType incidentOn(int edgeId) {
        return incidents.get(edgeId);
    }

    public Map<Integer, IncidentType> activeIncidents() {
        return new HashMap<>(incidents);
    }

    /** Travel time for this street segment RIGHT NOW: normal time times the incident slowdown. */
    public double currentMinutes(Edge edge) {
        IncidentType type = incidents.get(edge.id());
        return type == null ? edge.baseMinutes() : edge.baseMinutes() * type.slowdown;
    }

    // ---- snapping a GPS point to the map ---------------------------------------------------

    /** The junction closest to this point. Checks every junction: simple, and fast enough for a city. */
    public NodeHit nearestNode(double lat, double lon) {
        int best = -1;
        double bestMeters = Double.MAX_VALUE;
        for (int i = 0; i < nodeLat.length; i++) {
            double d = Geo.nearbyMeters(lat, lon, nodeLat[i], nodeLon[i]);
            if (d < bestMeters) {
                bestMeters = d;
                best = i;
            }
        }
        return new NodeHit(best, bestMeters);
    }

    /** The street segment closest to this point, if one is within maxMeters. */
    public Optional<EdgeHit> nearestEdge(double lat, double lon, double maxMeters) {
        Edge best = null;
        double bestMeters = Double.MAX_VALUE;
        for (Edge e : edges) {
            double[][] g = e.geometry();
            for (int i = 0; i + 1 < g.length; i++) {
                double d = Geo.pointToSegmentMeters(lat, lon, g[i][0], g[i][1], g[i + 1][0], g[i + 1][1]);
                if (d < bestMeters) {
                    bestMeters = d;
                    best = e;
                }
            }
        }
        return best != null && bestMeters <= maxMeters ? Optional.of(new EdgeHit(best, bestMeters)) : Optional.empty();
    }
}
