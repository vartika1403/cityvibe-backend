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
 * Fetches events from the Ticketmaster Discovery API and maps them into
 * CityVibe {@link Event} entities. Used by the seeding endpoint for testing.
 *
 * Note: Ticketmaster's catalog does not include India, so city=Bangalore
 * returns nothing. Use a covered city (e.g. Chicago, London) to get real data.
 */
@Service
public class TicketmasterService {

    private static final String API_URL =
            "https://app.ticketmaster.com/discovery/v2/events.json";

    private final String apiKey;
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();
    private final ObjectMapper mapper = new ObjectMapper();

    public TicketmasterService(@Value("${ticketmaster.api-key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    /**
     * Fetch events for a city, optionally filtered by keyword and classification.
     * Returns mapped (unsaved) Event entities.
     */
    public List<Event> fetchEvents(String city, String keyword, String classification) {
        StringBuilder url = new StringBuilder(API_URL)
                .append("?apikey=").append(enc(apiKey))
                .append("&city=").append(enc(city))
                .append("&size=50&sort=date,asc");
        if (keyword != null && !keyword.isBlank()) {
            url.append("&keyword=").append(enc(keyword));
        }
        if (classification != null && !classification.isBlank()) {
            url.append("&classificationName=").append(enc(classification));
        }

        JsonNode root = get(url.toString());
        JsonNode events = root.path("_embedded").path("events");

        List<Event> result = new ArrayList<>();
        if (events.isArray()) {
            for (JsonNode node : events) {
                result.add(mapEvent(node, classification));
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
                throw new RuntimeException("Ticketmaster returned HTTP "
                        + response.statusCode() + ": " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Failed to call Ticketmaster: " + e.getMessage(), e);
        }
    }

    private Event mapEvent(JsonNode node, String classificationOverride) {
        String title = text(node, "name");

        JsonNode start = node.path("dates").path("start");
        String localDate = text(start, "localDate");
        String localTime = text(start, "localTime");
        String dateTime = null;
        if (localDate != null) {
            dateTime = localDate + "T" + (localTime != null ? localTime : "00:00:00");
        }

        // Category: prefer the caller's classification, else the TM segment/genre.
        JsonNode classification = node.path("classifications").path(0);
        String category = classificationOverride;
        if (category == null || category.isBlank()) {
            category = text(classification.path("segment"), "name");
            if (category == null) {
                category = text(classification.path("genre"), "name");
            }
        }
        if (category == null) {
            category = "Other";
        }

        JsonNode venue = node.path("_embedded").path("venues").path(0);
        String venueName = text(venue, "name");
        String cityName = text(venue.path("city"), "name");
        String line1 = text(venue.path("address"), "line1");
        String venueAddress = joinNonBlank(", ", line1, cityName);

        String imageUrl = firstImageUrl(node.path("images"));
        String description = text(node, "info");
        if (description == null) {
            description = text(node, "pleaseNote");
        }
        if (description == null) {
            description = title; // fallback so the column is never null
        }

        String organizer = text(node.path("promoter"), "name");
        if (organizer == null) {
            organizer = "Ticketmaster";
        }

        String price = formatPrice(node.path("priceRanges").path(0));

        return new Event(title, category, description, imageUrl, dateTime,
                venueName, venueAddress, organizer, price);
    }

    private String firstImageUrl(JsonNode images) {
        if (images.isArray()) {
            String best = null;
            int bestWidth = -1;
            for (JsonNode img : images) {
                int width = img.path("width").asInt(0);
                if (width > bestWidth) {
                    bestWidth = width;
                    best = text(img, "url");
                }
            }
            return best;
        }
        return null;
    }

    private String formatPrice(JsonNode priceRange) {
        if (priceRange.isMissingNode() || priceRange.isNull()) {
            return "See listing";
        }
        JsonNode min = priceRange.path("min");
        JsonNode max = priceRange.path("max");
        String currency = text(priceRange, "currency");
        if (min.isMissingNode() && max.isMissingNode()) {
            return "See listing";
        }
        String cur = currency != null ? currency + " " : "";
        if (!min.isMissingNode() && !max.isMissingNode()) {
            return cur + min.asText() + "-" + max.asText();
        }
        JsonNode one = !min.isMissingNode() ? min : max;
        return cur + one.asText();
    }

    private static String text(JsonNode node, String field) {
        JsonNode v = node.path(field);
        return (v.isMissingNode() || v.isNull() || v.asText().isBlank()) ? null : v.asText();
    }

    private static String joinNonBlank(String sep, String... parts) {
        List<String> kept = new ArrayList<>();
        for (String p : parts) {
            if (p != null && !p.isBlank()) {
                kept.add(p);
            }
        }
        return kept.isEmpty() ? null : String.join(sep, kept);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s == null ? "" : s, StandardCharsets.UTF_8);
    }
}
