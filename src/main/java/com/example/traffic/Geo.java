package com.example.traffic;

/** Distance maths on latitude/longitude. Everything returns metres. */
final class Geo {

    private static final double EARTH_RADIUS_M = 6_371_000;
    private static final double METRES_PER_DEG_LAT = 110_540;
    private static final double METRES_PER_DEG_LON_AT_EQUATOR = 111_320;

    private Geo() {
    }

    /** Distance between two points on the globe (haversine formula). */
    static double meters(double lat1, double lon1, double lat2, double lon2) {
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * EARTH_RADIUS_M * Math.asin(Math.sqrt(a));
    }

    /**
     * Distance from point P to the line segment A-B.
     * Over a few kilometres the earth is flat enough, so we convert degrees to metres around P
     * and use plain geometry: project P onto the segment, clamp to its ends, measure.
     */
    static double pointToSegmentMeters(double pLat, double pLon,
                                       double aLat, double aLon, double bLat, double bLon) {
        double lonScale = METRES_PER_DEG_LON_AT_EQUATOR * Math.cos(Math.toRadians(pLat));
        double ax = (aLon - pLon) * lonScale, ay = (aLat - pLat) * METRES_PER_DEG_LAT;
        double bx = (bLon - pLon) * lonScale, by = (bLat - pLat) * METRES_PER_DEG_LAT;
        double vx = bx - ax, vy = by - ay;
        double len2 = vx * vx + vy * vy;
        double t = len2 == 0 ? 0 : -(ax * vx + ay * vy) / len2;   // where P projects along A-B
        t = Math.max(0, Math.min(1, t));                          // stay on the segment
        return Math.hypot(ax + t * vx, ay + t * vy);
    }

    /** Fast approximate distance in metres between two nearby points. */
    static double nearbyMeters(double lat1, double lon1, double lat2, double lon2) {
        double lonScale = METRES_PER_DEG_LON_AT_EQUATOR * Math.cos(Math.toRadians(lat1));
        return Math.hypot((lon2 - lon1) * lonScale, (lat2 - lat1) * METRES_PER_DEG_LAT);
    }
}
