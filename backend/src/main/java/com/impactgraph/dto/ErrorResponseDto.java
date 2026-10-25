package com.impactgraph.dto;

/**
 * Standard error response DTO for API clients.
 *
 * @param status    HTTP status code (e.g. 400, 404, 500)
 * @param error     HTTP status error description (e.g. "Bad Request", "Not Found")
 * @param message   client-safe explanation of the error
 * @param timestamp ISO-8601 formatted timestamp of when the error occurred
 */
public record ErrorResponseDto(
        int status,
        String error,
        String message,
        String timestamp
) {
}
