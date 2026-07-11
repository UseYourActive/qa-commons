package dev.qacommons.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qacommons.api.internal.HttpEngine;
import dev.qacommons.api.internal.RawResponse;
import dev.qacommons.api.internal.ResultClassifier;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.core.json.JsonMapperFactory;

/**
 * Base class for one API resource. Not thread-safe for concurrent use of the
 * same instance across threads: construct one Endpoint per test (or per
 * thread) rather than sharing an instance.
 *
 * <p>RestAssured is an implementation detail confined to
 * {@code dev.qacommons.api.internal} - it never appears in this class's
 * signature, and subclasses/tests never see it either.
 *
 * <p>Usage:
 * <pre>{@code
 * public final class WidgetsEndpoint extends Endpoint<CreateWidgetRequest, WidgetResponse, ErrorResponse> {
 *     public WidgetsEndpoint(QaConfig config) {
 *         super(config, "/widgets", WidgetResponse.class, ErrorResponse.class);
 *     }
 *
 *     public ApiResult<WidgetResponse, ErrorResponse> create(CreateWidgetRequest request) {
 *         return post(request);
 *     }
 * }
 * }</pre>
 */
public abstract class Endpoint<TReq, TRes, TErr> {

    private final HttpEngine engine;
    private final ObjectMapper mapper;
    private final String basePath;
    private final Class<TRes> successType;
    private final Class<TErr> errorType;

    protected Endpoint(QaConfig config, String basePath, Class<TRes> successType, Class<TErr> errorType) {
        this.engine = new HttpEngine(config);
        this.mapper = JsonMapperFactory.newMapper();
        this.basePath = basePath;
        this.successType = successType;
        this.errorType = errorType;
    }

    protected ApiResult<TRes, TErr> get(String pathSuffix, Object... pathParams) {
        return execute("GET", basePath + pathSuffix, pathParams, null);
    }

    protected ApiResult<TRes, TErr> post(TReq body) {
        return execute("POST", basePath, new Object[0], writeBody(body));
    }

    protected ApiResult<TRes, TErr> post(String pathSuffix, TReq body, Object... pathParams) {
        return execute("POST", basePath + pathSuffix, pathParams, writeBody(body));
    }

    protected ApiResult<TRes, TErr> put(String pathSuffix, TReq body, Object... pathParams) {
        return execute("PUT", basePath + pathSuffix, pathParams, writeBody(body));
    }

    protected ApiResult<TRes, TErr> delete(String pathSuffix, Object... pathParams) {
        return execute("DELETE", basePath + pathSuffix, pathParams, null);
    }

    private String writeBody(TReq body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize request body: " + body, e);
        }
    }

    private ApiResult<TRes, TErr> execute(String method, String path, Object[] pathParams, String jsonBody) {
        RawResponse response = engine.execute(method, path, pathParams, jsonBody);
        return ResultClassifier.classify(response, successType, errorType, mapper);
    }
}
