package com.cityvibe.service;

import com.cityvibe.model.Event;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Fetches events from the SerpApi Google Events engine and maps them into
 * CityVibe {@link Event} entities.
 *
 * Unlike Ticketmaster, Google Events has real India inventory, so queries like
 * q="dance classes in Bangalore" &amp; location="Bangalore,Karnataka,India" return data.
 *
 * API reference: https://serpapi.com/google-events-api
 */
@Service
public class SerpApiEventsService {

    private static final String API_URL = "https://serpapi.com/search";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public SerpApiEventsService(@Value("${serpapi.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Fetch events for a free-text query and optional location.
     * Returns mapped (unsaved) Event entities.
     *
     * @param query    e.g. "dance classes in Bangalore" (required)
     * @param location e.g. "Bangalore,Karnataka,India" (optional)
     * @param category category label applied to every mapped event (optional)
     */
    public List<Event> fetchEvents(String query, String location, String category) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("SerpApi key not configured (serpapi.api-key)");
        }

        StringBuilder url = new StringBuilder(API_URL)
                .append("?engine=google_events")
                .append("&q=").append(enc(query))
                .append("&api_key=").append(enc(apiKey));
        if (location != null && !location.isBlank()) {
            url.append("&location=").append(enc(location));
        }

        JsonNode root = get(url.toString());
        JsonNode error = root.path("error");
        if (!error.isMissingNode() && !error.isNull()) {
            String msg = error.asText();
            // "Google hasn't returned any results" is a normal empty result, not a
            // failure — return an empty list rather than blowing up with a 500.
            if (msg != null && msg.toLowerCase().contains("hasn't returned any results")) {
                return new ArrayList<>();
            }
            throw new RuntimeException("SerpApi error: " + msg);
        }

        JsonNode events = root.path("events_results");
        List<Event> result = new ArrayList<>();
        if (events.isArray()) {
            for (JsonNode node : events) {
                result.add(mapEvent(node, category));
            }
        }
        return result;
    }

    private JsonNode get(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                throw new RuntimeException("SerpApi returned HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call SerpApi: " + e.getMessage(), e);
        }
    }

    private Event mapEvent(JsonNode node, String category) {
        String title = text(node, "title");

        // Google Events dates are free-form (e.g. "Fri, Jul 25, 10 – 11 AM"),
        // not ISO-8601. Prefer the human "when" string, fall back to start_date.
        JsonNode date = node.path("date");
        String dateTime = text(date, "when");
        if (dateTime == null) {
            dateTime = text(date, "start_date");
        }

        if (category == null || category.isBlank()) {
            category = "Event";
        }

        // address is an array of strings, e.g. ["Venue Name, Street", "City, State"].
        JsonNode address = node.path("address");
        String venueAddress = joinArray(address, ", ");

        JsonNode venue = node.path("venue");
        String venueName = text(venue, "name");

        String imageUrl = text(node, "image");
        if (imageUrl == null) {
            imageUrl = text(node, "thumbnail");
        }

        String description = text(node, "description");
        if (description == null) {
            description = title; // never leave the column null
        }

        // Google Events has no organizer field; use the venue, else a stable label.
        String organizer = venueName != null ? venueName : "Google Events";

        // No structured price in this engine; ticket links exist but not amounts.
        String price = "See listing";

        return new Event(title, category, description, imageUrl, dateTime,
                venueName, venueAddress, organizer, price);
    }

    private static String joinArray(JsonNode arrayNode, String sep) {
        if (!arrayNode.isArray() || arrayNode.isEmpty()) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        for (JsonNode n : arrayNode) {
            if (!n.isNull() && !n.asText().isBlank()) {
                parts.add(n.asText());
            }
        }
        return parts.isEmpty() ? null : String.join(sep, parts);
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
