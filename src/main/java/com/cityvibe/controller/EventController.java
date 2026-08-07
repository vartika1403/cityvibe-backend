package com.cityvibe.controller;

import com.cityvibe.model.Event;
import com.cityvibe.repository.EventRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// CORS is configured centrally in WebConfig (app.cors.allowed-origins).
@RestController
@RequestMapping("/api/events")
public class EventController {

    private final EventRepository eventRepository;

    public EventController(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }

    // GET /api/events  (optional ?category= filter)
    @GetMapping
    public List<Event> getAllEvents(@RequestParam(required = false) String category) {
        if (category != null && !category.isBlank() && !category.equalsIgnoreCase("All")) {
            return eventRepository.findByCategoryIgnoreCase(category.trim());
        }
        return eventRepository.findAll();
    }

    // GET /api/events/{id}
    @GetMapping("/{id}")
    public ResponseEntity<Event> getEventById(@PathVariable Long id) {
        return eventRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
