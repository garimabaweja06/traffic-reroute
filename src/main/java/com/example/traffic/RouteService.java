package com.example.traffic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

/**
 * The "brain": turns clicks and coordinates into calls to the road network and Dijkstra.
 * @Service tells Spring to create ONE of these and share it, so every request sees the same incidents.
 */
@Service
public class RouteService {

    /** A click further than this from any junction is not "on the map". */
    private static final double MAX_SNAP_TO_JUNCTION_M = 500;
    /** An incident report further than this from any road is rejected. */
    private static final double MAX_SNAP_TO_ROAD_M = 60;

    private final RoadNetwork net;
    private final NetworkResponse networkResponse;     // built once: the roads never change

    public RouteService(RoadNetwork net) {
        this.net = net;
        List<NetworkResponse.EdgeDto> dtos = new ArrayList<>();
        for (Edge e : net.edges()) {
            dtos.add(new NetworkResponse.EdgeDto(e.id(), e.name(), e.highway(), round(e.lengthM(), 1),
                    round(e.baseMinutes(), 2), e.oneway(), roundPoints(e.geometry())));
        }
        this.networkResponse = new NetworkResponse(net.source(), net.bounds(), net.nodeCount(), dtos);
    }

    public NetworkResponse network() {
        return networkResponse;
    }

    // ---- routing -------------------------------------------------------------------------

    public RouteResponse findRoute(double fromLat, double fromLon, double toLat, double toLon) {
        int start = snapToJunction(fromLat, fromLon, "Start");
        int end = snapToJunction(toLat, toLon, "Destination");
        if (start == end) {
            throw new BadRequestException("Start and destination are at the same junction. Pick two different points.");
        }
        Dijkstra.Result result = Dijkstra.shortestPath(net, start, end);
        if (result == null) {
            throw new NoRouteException("No route available (roads may be blocked)");
        }

        List<double[]> points = new ArrayList<>();
        List<Integer> edgeIds = new ArrayList<>();
        List<String> streets = new ArrayList<>();
        for (RoadNetwork.Arc arc : result.arcs()) {
            Edge e = arc.edge();
            edgeIds.add(e.id());
            double[][] g = e.geometry();
            for (int i = 0; i < g.length; i++) {
                double[] p = arc.forward() ? g[i] : g[g.length - 1 - i];     // drive the segment in our direction
                if (i == 0 && !points.isEmpty()) {
                    continue;                                                  // this point repeats the previous end
                }
                points.add(p);
            }
            String name = e.name();
            if (name != null && (streets.isEmpty() || !streets.get(streets.size() - 1).equals(name))) {
                streets.add(name);
            }
        }
        return new RouteResponse(round(result.minutes(), 1), round(result.meters(), 0),
                roundPoints(points.toArray(new double[0][])), edgeIds, streets,
                new double[]{net.lat(start), net.lon(start)}, new double[]{net.lat(end), net.lon(end)});
    }

    private int snapToJunction(double lat, double lon, String label) {
        RoadNetwork.NodeHit hit = net.nearestNode(lat, lon);
        if (hit.meters() > MAX_SNAP_TO_JUNCTION_M) {
            throw new BadRequestException(label + " is more than " + (int) MAX_SNAP_TO_JUNCTION_M
                    + " m from the nearest road. Click closer to a road.");
        }
        return hit.node();
    }

    // ---- incidents -----------------------------------------------------------------------

    public IncidentDto reportIncident(IncidentRequest req) {
        Edge edge;
        if (req.edgeId() != null) {
            edge = net.edge(req.edgeId());
            if (edge == null) {
                throw new BadRequestException("Unknown road: " + req.edgeId());
            }
        } else if (req.lat() != null && req.lon() != null) {
            edge = net.nearestEdge(req.lat(), req.lon(), MAX_SNAP_TO_ROAD_M)
                    .orElseThrow(() -> new BadRequestException(
                            "No road within " + (int) MAX_SNAP_TO_ROAD_M + " m of that position"))
                    .edge();
        } else {
            throw new BadRequestException("Send either edgeId, or both lat and lon");
        }
        net.reportIncident(edge.id(), req.type());
        return new IncidentDto(edge.id(), req.type().name(), edge.name());
    }

    public void clearIncident(int edgeId) {
        if (net.edge(edgeId) == null) {
            throw new BadRequestException("Unknown road: " + edgeId);
        }
        net.clearIncident(edgeId);
    }

    public void clearAllIncidents() {
        net.clearAllIncidents();
    }

    public List<IncidentDto> incidents() {
        List<IncidentDto> list = new ArrayList<>();
        for (Map.Entry<Integer, IncidentType> en : net.activeIncidents().entrySet()) {
            list.add(new IncidentDto(en.getKey(), en.getValue().name(), net.edge(en.getKey()).name()));
        }
        return list;
    }

    // ---- helpers -------------------------------------------------------------------------

    private static double round(double v, int decimals) {
        double f = Math.pow(10, decimals);
        return Math.round(v * f) / f;
    }

    private static double[][] roundPoints(double[][] pts) {          // 6 decimals is about 10 cm
        double[][] out = new double[pts.length][];
        for (int i = 0; i < pts.length; i++) {
            out[i] = new double[]{round(pts[i][0], 6), round(pts[i][1], 6)};
        }
        return out;
    }
}
