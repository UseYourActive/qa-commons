package dev.qacommons.api.internal;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qacommons.api.ApiResult;

/**
 * Thread-safe: stateless, takes its {@link ObjectMapper} per call rather than
 * holding one.
 */
public final class ResultClassifier {

    private ResultClassifier() {
    }

    /**
     * {@code successType} is a {@link JavaType} rather than a {@code Class<T>}
     * so that callers can carry full generic fidelity (e.g. a
     * {@code PageResponse<Foo>}) through to deserialization - a plain
     * {@code Class<T>} would erase the element type. {@code errorType} stays
     * {@code Class<E>}: no caller of this internal class needs a generic
     * error body today.
     */
    public static <T, E> ApiResult<T, E> classify(RawResponse response, JavaType successType, Class<E> errorType,
            ObjectMapper mapper) {
        int status = response.status();

        if (status >= 200 && status < 300) {
            try {
                T body = mapper.readValue(response.body(), successType);
                return new ApiResult.Success<>(status, response.headers(), body);
            } catch (Exception e) {
                return new ApiResult.Unparsed<>(status, response.headers(), response.body(), e);
            }
        }

        try {
            E error = mapper.readValue(response.body(), errorType);
            return new ApiResult.Failure<>(status, response.headers(), error);
        } catch (Exception e) {
            return new ApiResult.Unparsed<>(status, response.headers(), response.body(), e);
        }
    }
}
