package com.example.traffic;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Creates the RoadNetwork once, when the app starts. It looks for road data in this order:
 *   1. the saved file (data/ambala-cantt.json), from an earlier run
 *   2. a fresh download of the real roads from OpenStreetMap (then saved to that file)
 *   3. a small built-in SAMPLE network, so the app still starts (the page shows a warning)
 */
@Configuration
public class NetworkConfig {

    private static final Logger log = LoggerFactory.getLogger(NetworkConfig.class);

    // Public Overpass API servers. We try them in order.
    private static final String[] OVERPASS_SERVERS = {
            "https://overpass-api.de/api/interpreter",
            "https://overpass.private.coffee/api/interpreter",
            "https://overpass.kumi.systems/api/interpreter"
    };
    private static final int MIN_EDGES = 10;

    @Bean
    public RoadNetwork roadNetwork(
            @Value("${traffic.osm.south}") double south,
            @Value("${traffic.osm.west}") double west,
            @Value("${traffic.osm.north}") double north,
            @Value("${traffic.osm.east}") double east,
            @Value("${traffic.osm.cache-file}") String cacheFile,
            @Value("${traffic.osm.download:true}") boolean download) {

        ObjectMapper mapper = new ObjectMapper();
        Path cache = Path.of(cacheFile);

        // 1. saved file
        if (Files.exists(cache)) {
            try {
                RoadNetwork net = load(mapper, Files.readString(cache), "OpenStreetMap (saved file)");
                log.info("Loaded saved OpenStreetMap roads from {}", cache.toAbsolutePath());
                return net;
            } catch (Exception e) {
                log.warn("Could not use saved file {}: {}", cache, e.toString());
            }
        }

        // 2. download
        if (download) {
            try {
                log.info("Downloading roads from OpenStreetMap (first run only, this can take a minute)...");
                String json = downloadFromOverpass(buildQuery(south, west, north, east));
                RoadNetwork net = load(mapper, json, "OpenStreetMap");
                if (cache.getParent() != null) {
                    Files.createDirectories(cache.getParent());
                }
                Files.writeString(cache, json);
                log.info("Saved the roads to {}", cache.toAbsolutePath());
                return net;
            } catch (Exception e) {
                log.warn("Download from OpenStreetMap failed: {}", e.toString());
            }
        }

        // 3. built-in sample
        log.warn("USING SAMPLE DATA, NOT REAL ROADS. Check your internet connection and restart.");
        try (InputStream in = NetworkConfig.class.getResourceAsStream("/osm/sample-network.json")) {
            if (in == null) {
                throw new IllegalStateException("Missing /osm/sample-network.json");
            }
            return load(mapper, new String(in.readAllBytes(), StandardCharsets.UTF_8),
                    "SAMPLE data (could not get OpenStreetMap)");
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the sample network", e);
        }
    }

    private static RoadNetwork load(ObjectMapper mapper, String json, String source) throws IOException {
        Map<String, Object> root = mapper.readValue(json, new TypeReference<Map<String, Object>>() {
        });
        RoadNetwork net = OsmParser.parse(root, source);
        if (net.edges().size() < MIN_EDGES) {
            throw new IllegalStateException("Only " + net.edges().size() + " roads found; the area is too small");
        }
        log.info("Road network ready: {} junctions, {} street segments, source = {}",
                net.nodeCount(), net.edges().size(), source);
        return net;
    }

    /** Overpass query language: "all drivable ways inside this box, plus their points". */
    static String buildQuery(double south, double west, double north, double east) {
        return "[out:json][timeout:90];(way[\"highway\"~\"^(motorway|trunk|primary|secondary|tertiary|unclassified"
                + "|residential|living_street|motorway_link|trunk_link|primary_link|secondary_link|tertiary_link)$\"]"
                + "(" + south + "," + west + "," + north + "," + east + "););out body;>;out skel qt;";
    }

    private static String downloadFromOverpass(String query) throws IOException, InterruptedException {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
        String form = "data=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        IOException last = null;
        for (String server : OVERPASS_SERVERS) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(server))
                        .timeout(Duration.ofSeconds(120))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .header("User-Agent", "traffic-reroute-student-project/1.0")
                        .POST(HttpRequest.BodyPublishers.ofString(form))
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200 && response.body().contains("\"elements\"")) {
                    return response.body();
                }
                last = new IOException(server + " answered HTTP " + response.statusCode());
            } catch (IOException e) {
                last = e;
            }
            log.warn("Overpass server {} did not work: {}", server, last.getMessage());
        }
        throw last != null ? last : new IOException("No Overpass server available");
    }
}
