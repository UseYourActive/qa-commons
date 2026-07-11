package dev.qacommons.api.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qacommons.api.ApiResult;

/**
 * Thread-safe: stateless, takes its {@link ObjectMapper} per call rather than
 * holding one.
 */
public final class ResultClassifier {

    private ResultClassifier() {
    }

    public static <T, E> ApiResult<T, E> classify(RawResponse response, Class<T> successType, Class<E> errorType,
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
