package com.cityvibe.controller;

import com.cityvibe.model.Event;
import com.cityvibe.repository.EventRepository;
import com.cityvibe.service.TicketmasterService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Admin-only endpoint to seed the events table from the Ticketmaster Discovery API.
 *
 * Requires an X-Admin-Token header matching the ADMIN_SEED_TOKEN env var.
 * If no token is configured, the endpoint is disabled (fails closed).
 *
 * Example:
 *   curl -X POST -H "X-Admin-Token: $ADMIN_SEED_TOKEN" \
 *     "http://localhost:8080/api/events/seed/ticketmaster?city=Chicago&keyword=dance&category=Music"
 *
 * Reminder: Ticketmaster has no India inventory, so city=Bangalore returns 0.
 */
@RestController
@RequestMapping("/api/events/seed")
public class TicketmasterSeedController {

    private final TicketmasterService ticketmasterService;
    private final EventRepository eventRepository;
    private final String adminToken;

    public TicketmasterSeedController(TicketmasterService ticketmasterService,
                                      EventRepository eventRepository,
                                      @Value("${app.admin.seed-token:}") String adminToken) {
        this.ticketmasterService = ticketmasterService;
        this.eventRepository = eventRepository;
        this.adminToken = adminToken;
    }

    @PostMapping("/ticketmaster")
    public ResponseEntity<Map<String, Object>> seedFromTicketmaster(
            @RequestHeader(value = "X-Admin-Token", required = false) String token,
            @RequestParam(defaultValue = "Chicago") String city,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {

        // Fail closed: reject if no token is configured or it does not match.
        if (adminToken == null || adminToken.isBlank() || !adminToken.equals(token)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body(Map.of("error", "Invalid or missing X-Admin-Token"));
        }

        List<Event> fetched = ticketmasterService.fetchEvents(city, keyword, category);

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
        body.put("city", city);
        body.put("keyword", keyword);
        body.put("category", category);
        body.put("fetched", fetched.size());
        body.put("inserted", saved.size());
        body.put("skippedDuplicatesOrEmpty", skipped);
        body.put("insertedTitles", savedTitles);
        return ResponseEntity.ok(body);
    }
}
