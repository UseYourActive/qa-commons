package dev.qacommons.api.internal;

import java.util.Map;

/**
 * Thread-safe: immutable record. Bridges {@link HttpEngine}'s RestAssured
 * call to {@link ResultClassifier} without either side referencing
 * {@code io.restassured} types.
 */
public record RawResponse(int status, Map<String, String> headers, String body) {
}
