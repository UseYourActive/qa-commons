package dev.qacommons.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.qacommons.api.internal.HttpEngine;
import dev.qacommons.api.internal.RawResponse;
import dev.qacommons.api.internal.ResultClassifier;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.core.json.JsonMapperFactory;
import java.util.Map;

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
 *
 * <p>A response that is itself generic (e.g. a paginated envelope) can't be
 * expressed as a {@code Class<TRes>} - type erasure would lose the element
 * type. Use the {@link TypeReference} constructor instead:
 * <pre>{@code
 * public final class WidgetsEndpoint extends Endpoint<Void, PageResponse<WidgetResponse>, ErrorResponse> {
 *     public WidgetsEndpoint(QaConfig config) {
 *         super(config, "/widgets", new TypeReference<PageResponse<WidgetResponse>>() {}, ErrorResponse.class);
 *     }
 *
 *     public ApiResult<PageResponse<WidgetResponse>, ErrorResponse> list(int page, int size) {
 *         return getWithQuery("", Map.of("page", page, "size", size));
 *     }
 * }
 * }</pre>
 */
public abstract class Endpoint<TReq, TRes, TErr> {

    private final HttpEngine engine;
    private final ObjectMapper mapper;
    private final String basePath;
    private final JavaType successType;
    private final Class<TErr> errorType;

    protected Endpoint(QaConfig config, String basePath, Class<TRes> successType, Class<TErr> errorType) {
        this.engine = new HttpEngine(config);
        this.mapper = JsonMapperFactory.newMapper();
        this.basePath = basePath;
        this.successType = mapper.getTypeFactory().constructType(successType);
        this.errorType = errorType;
    }

    /**
     * For a success body that is itself generic (e.g. {@code PageResponse<T>})
     * - a plain {@code Class<TRes>} would erase {@code T} and deserialize its
     * elements as raw {@code LinkedHashMap}s. Pass an anonymous
     * {@code TypeReference} subclass to preserve the full type:
     * {@code new TypeReference<PageResponse<WidgetResponse>>() {}}.
     */
    protected Endpoint(QaConfig config, String basePath, TypeReference<TRes> successType, Class<TErr> errorType) {
        this.engine = new HttpEngine(config);
        this.mapper = JsonMapperFactory.newMapper();
        this.basePath = basePath;
        this.successType = mapper.getTypeFactory().constructType(successType);
        this.errorType = errorType;
    }

    protected ApiResult<TRes, TErr> get(String pathSuffix, Object... pathParams) {
        return execute("GET", basePath + pathSuffix, pathParams, null);
    }

    /**
     * Like {@link #get(String, Object...)}, but for query parameters instead
     * of path parameters. A distinctly-named method rather than an overload
     * of {@code get} - a {@code (String, Object...)} and a
     * {@code (String, Map<String,Object>)} overload of the same name are a
     * real Java varargs-overload-resolution trap once a caller passes both a
     * map and further positional args. Values are passed through
     * RestAssured's {@code queryParams(Map)}, which URL-encodes them - never
     * build the query string yourself.
     */
    protected ApiResult<TRes, TErr> getWithQuery(String pathSuffix, Map<String, Object> queryParams) {
        return execute("GET", basePath + pathSuffix, new Object[0], queryParams, null);
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

    private ApiResult<TRes, TErr> execute(String method, String path, Object[] pathParams,
            Map<String, Object> queryParams, String jsonBody) {
        RawResponse response = engine.execute(method, path, pathParams, queryParams, jsonBody);
        return ResultClassifier.classify(response, successType, errorType, mapper);
    }
}
