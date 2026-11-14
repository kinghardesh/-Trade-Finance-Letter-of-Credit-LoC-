package com.tradefinance.loc.api.dto;

import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Uniform error body returned by every endpoint. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String error,
        String message,
        Map<String, String> fieldErrors,
        OffsetDateTime timestamp) {

    public static ErrorResponse of(String error, String message) {
        return new ErrorResponse(error, message, null, OffsetDateTime.now());
    }
}
