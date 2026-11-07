package com.example.traffic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/**
 * Dijkstra's shortest-path algorithm: the same idea as before, now on the real road network.
 * The cost of each street segment is its CURRENT travel time, which includes any incident.
 */
public final class Dijkstra {

    /** The answer: the street segments to drive along, in order, plus total minutes and metres. */
    public record Result(List<RoadNetwork.Arc> arcs, double minutes, double meters) {
    }

    private record Entry(int node, double minutes) {
    }

    private Dijkstra() {
    }

    /** @return the fastest route from node `start` to node `end`, or null if no route exists. */
    public static Result shortestPath(RoadNetwork net, int start, int end) {
        int n = net.nodeCount();
        double[] best = new double[n];                         // best[x] = fastest known time to reach x
        RoadNetwork.Arc[] cameBy = new RoadNetwork.Arc[n];     // the last street segment used to reach x
        int[] cameFrom = new int[n];                           // the junction we were at before x
        Arrays.fill(best, Double.POSITIVE_INFINITY);
        Arrays.fill(cameFrom, -1);

        PriorityQueue<Entry> queue = new PriorityQueue<>(Comparator.comparingDouble(Entry::minutes));
        best[start] = 0;
        queue.add(new Entry(start, 0));

        while (!queue.isEmpty()) {
            Entry current = queue.poll();                      // the closest junction not yet finished
            int u = current.node();
            if (current.minutes() > best[u]) {
                continue;                                      // an old, slower entry: skip it
            }
            if (u == end) {
                return buildResult(cameBy, cameFrom, start, end, best[end]);
            }
            for (RoadNetwork.Arc arc : net.arcsFrom(u)) {
                double cost = net.currentMinutes(arc.edge());
                if (cost == Double.POSITIVE_INFINITY) {
                    continue;                                  // blocked road: cannot use it
                }
                double newTime = best[u] + cost;
                if (newTime < best[arc.to()]) {                // a faster way to reach arc.to()
                    best[arc.to()] = newTime;
                    cameBy[arc.to()] = arc;
                    cameFrom[arc.to()] = u;
                    queue.add(new Entry(arc.to(), newTime));
                }
            }
        }
        return null;                                           // never reached the end
    }

    // Walk backwards from the end to the start, then flip the list.
    private static Result buildResult(RoadNetwork.Arc[] cameBy, int[] cameFrom, int start, int end, double minutes) {
        List<RoadNetwork.Arc> arcs = new ArrayList<>();
        double meters = 0;
        for (int v = end; v != start; v = cameFrom[v]) {
            arcs.add(cameBy[v]);
            meters += cameBy[v].edge().lengthM();
        }
        Collections.reverse(arcs);
        return new Result(arcs, minutes, meters);
    }
}
