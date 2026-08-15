package com.cityvibe.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /api/events}, mirroring the client's CreateShowRequest.
 *
 * <p>Only title and category are required, matching the NOT NULL columns on {@code events}.
 * The client already trims its strings; they are trimmed again here because an API cannot
 * assume anything about who is calling it.
 */
public record CreateEventRequest(

        @NotBlank(message = "title is required")
        String title,

        @NotBlank(message = "category is required")
        String category,

        String description,

        /** ISO-8601 local date-time, e.g. "2026-07-15T19:30:00". */
        String dateTime,

        String duration,

        String venueName,

        String price) {
}
