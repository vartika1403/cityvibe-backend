package com.cityvibe.controller;

import com.cityvibe.model.Event;
import com.cityvibe.repository.EventRepository;
import com.cityvibe.service.SerpApiEventsService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Admin-only endpoints backed by the SerpApi Google Events engine.
 *
 * Both endpoints require an X-Admin-Token header matching the ADMIN_SEED_TOKEN
 * env var. If no token is configured, they are disabled (fail closed). This
 * guards the paid SerpApi key from being drained by anonymous callers.
 *
 * Search (fetch live, no save):
 *   curl -H "X-Admin-Token: $ADMIN_SEED_TOKEN" \
 *     "http://localhost:8080/api/events/serpapi/search?q=dance+classes+in+Bangalore&location=Bangalore,Karnataka,India"
 *
 * Seed (fetch and persist new events):
 *   curl -X POST -H "X-Admin-Token: $ADMIN_SEED_TOKEN" \
 *     "http://localhost:8080/api/events/serpapi/seed?q=dance+classes+in+Bangalore&location=Bangalore,Karnataka,India&category=Dance"
 */
@RestController
@RequestMapping("/api/events/serpapi")
public class SerpApiEventsController {

    private final SerpApiEventsService serpApiEventsService;
    private final EventRepository eventRepository;
    private final String adminToken;

    public SerpApiEventsController(SerpApiEventsService serpApiEventsService,
                                   EventRepository eventRepository,
                                   @Value("${app.admin.seed-token:}") String adminToken) {
        this.serpApiEventsService = serpApiEventsService;
        this.eventRepository = eventRepository;
        this.adminToken = adminToken;
    }

    /** Fetch events live from Google Events and return them without persisting. */
    @GetMapping("/search")
    public ResponseEntity<?> search(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category) {

        ResponseEntity<Map<String, Object>> denied = checkToken(token);
        if (denied != null) {
            return denied;
        }

        List<Event> events = serpApiEventsService.fetchEvents(q, location, category);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", q);
        body.put("location", location);
        body.put("category", category);
        body.put("count", events.size());
        body.put("events", events);
        return ResponseEntity.ok(body);
    }

    /** Fetch events and persist any that are new (deduped by title). */
    @PostMapping("/seed")
    public ResponseEntity<Map<String, Object>> seed(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam String q,
            @RequestParam(required = false) String location,
            @RequestParam(required = false) String category) {

        ResponseEntity<Map<String, Object>> denied = checkToken(token);
        if (denied != null) {
            return denied;
        }

        List<Event> fetched = serpApiEventsService.fetchEvents(q, location, category);

        List<Event> toSave = new ArrayList<>();
        int skipped = 0;
        for (Event e : fetched) {
            if (e.getTitle() == null || e.getTitle().isBlank()
                    || eventRepository.existsByTitleIgnoreCase(e.getTitle())) {
                skipped++;
                continue;
            }
            toSave.add(e);
        }
        List<Event> saved = eventRepository.saveAll(toSave);

        List<String> savedTitles = new ArrayList<>();
        for (Event e : saved) {
            savedTitles.add(e.getTitle());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", q);
        body.put("location", location);
        body.put("category", category);
        body.put("fetched", fetched.size());
        body.put("inserted", saved.size());
        body.put("skippedDuplicatesOrEmpty", skipped);
        body.put("insertedTitles", savedTitles);
        return ResponseEntity.ok(body);
    }

    /** Returns a 403 response if the token is missing/invalid, else null. */
    private ResponseEntity<Map<String, Object>> checkToken(String token) {
        if (adminToken == null || adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or missing X-Admin-Token"));
        }
        return null;
    }
}
