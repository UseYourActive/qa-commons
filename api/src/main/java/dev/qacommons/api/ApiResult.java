package dev.qacommons.api;

import java.util.Map;

/**
 * Thread-safe: every variant is an immutable record.
 *
 * <p>Classification is exhaustive by construction: a 2xx response with a
 * body parseable as {@code T} is {@link Success}; a non-2xx response with a
 * body parseable as {@code E} is {@link Failure}; anything else (unparseable
 * body, unexpected shape) is {@link Unparsed}. There is no nullable-field
 * ambiguity - callers pattern-match, or use {@link #expectSuccess()} /
 * {@link #expectFailure()} for the common case.
 */
public sealed interface ApiResult<T, E> permits ApiResult.Success, ApiResult.Failure, ApiResult.Unparsed {

    int status();

    Map<String, String> headers();

    record Success<T, E>(int status, Map<String, String> headers, T body) implements ApiResult<T, E> {
    }

    record Failure<T, E>(int status, Map<String, String> headers, E error) implements ApiResult<T, E> {
    }

    record Unparsed<T, E>(int status, Map<String, String> headers, String raw, Throwable parseError)
            implements ApiResult<T, E> {
    }

    default T expectSuccess() {
        if (this instanceof Success<T, E> success) {
            return success.body();
        }
        throw new AssertionError("Expected Success but was " + describe());
    }

    default E expectFailure() {
        if (this instanceof Failure<T, E> failure) {
            return failure.error();
        }
        throw new AssertionError("Expected Failure but was " + describe());
    }

    private String describe() {
        return switch (this) {
            case Success<T, E> s -> "Success(status=%d, body=%s)".formatted(s.status(), s.body());
            case Failure<T, E> f -> "Failure(status=%d, error=%s)".formatted(f.status(), f.error());
            case Unparsed<T, E> u -> "Unparsed(status=%d, raw=%s)".formatted(u.status(), u.raw());
        };
    }
}
