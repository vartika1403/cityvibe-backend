package com.cityvibe.controller;

import com.cityvibe.dto.CreateEventRequest;
import com.cityvibe.model.Event;
import com.cityvibe.repository.EventRepository;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
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

    /**
     * POST /api/events — create one event.
     *
     * <p>Returns 201 with the saved event, including its generated id, and a Location header
     * pointing at {@code /api/events/{id}}. A body missing title or category is rejected with 400.
     */
    @PostMapping
    public ResponseEntity<Event> createEvent(@Valid @RequestBody CreateEventRequest request) {
        Event event = new Event();
        event.setTitle(trimmed(request.title()));
        event.setCategory(trimmed(request.category()));
        event.setDescription(trimmed(request.description()));
        event.setDateTime(trimmed(request.dateTime()));
        event.setDuration(trimmed(request.duration()));
        event.setVenueName(trimmed(request.venueName()));
        event.setPrice(trimmed(request.price()));

        Event saved = eventRepository.save(event);

        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.getId())
                .toUri();
        return ResponseEntity.created(location).body(saved);
    }

    private static String trimmed(String value) {
        return value == null ? null : value.trim();
    }
}
