package com.example.traffic;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

/** The web layer: turns HTTP requests into calls to RouteService. Contains no logic itself. */
@RestController
@RequestMapping("/api/v1")
public class TrafficController {

    private final RouteService service;

    // Spring passes in the shared RouteService ("dependency injection")
    public TrafficController(RouteService service) {
        this.service = service;
    }

    // GET /api/v1/network : every road with its shape, for drawing the map
    @GetMapping("/network")
    public NetworkResponse network() {
        return service.network();
    }

    // GET /api/v1/route?fromLat=..&fromLon=..&toLat=..&toLon=..
    @GetMapping("/route")
    public RouteResponse route(@RequestParam double fromLat, @RequestParam double fromLon,
                               @RequestParam double toLat, @RequestParam double toLon) {
        return service.findRoute(fromLat, fromLon, toLat, toLon);
    }

    // GET /api/v1/incidents : all active incidents
    @GetMapping("/incidents")
    public List<IncidentDto> incidents() {
        return service.incidents();
    }

    // POST /api/v1/incidents   body: {"edgeId":12,"type":"ROAD_BLOCK"}  or  {"lat":..,"lon":..,"type":"ACCIDENT"}
    @PostMapping("/incidents")
    public ResponseEntity<IncidentDto> report(@Valid @RequestBody IncidentRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.reportIncident(req));
    }

    // DELETE /api/v1/incidents/12 : clear the incident on road 12
    @DeleteMapping("/incidents/{edgeId}")
    public ResponseEntity<Void> clear(@PathVariable int edgeId) {
        service.clearIncident(edgeId);
        return ResponseEntity.noContent().build();
    }

    // DELETE /api/v1/incidents : clear everything (handy between demos)
    @DeleteMapping("/incidents")
    public ResponseEntity<Void> clearAll() {
        service.clearAllIncidents();
        return ResponseEntity.noContent().build();
    }
}
