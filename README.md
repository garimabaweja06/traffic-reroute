# Traffic Reroute: real roads of Ambala Cantt

Report a road incident on a real OpenStreetMap road network and watch the fastest route change.
Java 21, Spring Boot 3.3, Dijkstra, Leaflet + OpenStreetMap. No database or Docker yet: incidents live in memory.

## Demo

**1. Fastest route on real OpenStreetMap roads**

![Route](docs/root.png)

**2. A road block is reported, and the route changes**

![Road block](docs/roadblock.png)

**3. Several incidents together, and the route adapts again**

![Multiple incidents](docs/multiple%20incident.png)

## Run
1. Open the folder in IntelliJ (pom.xml -> Open as Project), wait for Maven to finish.
2. Run `TrafficApplication`.
3. Open http://localhost:8080

First run downloads the roads from OpenStreetMap (Overpass API) and saves them to `data/ambala-cantt.json`.
Later runs read that file, so they work offline. Commit the file to GitHub.
If the download fails, the app shows built-in SAMPLE roads and a yellow banner says so.

## Change the area
Edit `traffic.osm.south/west/north/east` in `application.properties`, delete `data/ambala-cantt.json`, restart.
Keep the box small (about 3 km x 3 km), or the page gets slow.

## API (`/api/v1`)
| Method | Path | Purpose |
|---|---|---|
| GET | /network | all roads with their shape |
| GET | /route?fromLat&fromLon&toLat&toLon | fastest route, avoids blocked roads |
| GET | /incidents | list incidents |
| POST | /incidents | body `{"edgeId":5,"type":"ROAD_BLOCK"}` or `{"lat":..,"lon":..,"type":"ACCIDENT"}` |
| DELETE | /incidents/{edgeId} | clear one |
| DELETE | /incidents | clear all |

Types: CONGESTION (x2 time), ACCIDENT (x3), ROAD_BLOCK (impassable).

## How it works
- `OsmParser`: turns OSM ways into a graph (junctions = nodes, street pieces = edges, one-way respected, curves kept for drawing).
- `Dijkstra`: PriorityQueue shortest path; edge cost = length / speed x incident slowdown.
- `RoadNetwork`: graph + live incidents (ConcurrentHashMap) + snapping clicks to the nearest road.
