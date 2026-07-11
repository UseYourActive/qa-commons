package dev.qacommons.api.internal;

import dev.qacommons.core.config.QaConfig;
import io.qameta.allure.restassured.AllureRestAssured;
import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Not thread-safe: builds one immutable {@link RequestSpecification}, with
 * its own {@link AllureRestAssured} and {@link LoggingFilter} instances, in
 * its constructor. Construct one HttpEngine (via one {@code Endpoint}) per
 * test/thread - never share an instance across threads.
 */
public final class HttpEngine {

    private final RequestSpecification spec;
    private final LoggingFilter loggingFilter;

    public HttpEngine(QaConfig config) {
        this.loggingFilter = new LoggingFilter();
        int timeoutMillis = (int) config.requestTimeout().toMillis();
        this.spec = new RequestSpecBuilder()
                .setBaseUri(config.baseUrl())
                .setConfig(RestAssuredConfig.config().httpClient(HttpClientConfig.httpClientConfig()
                        .setParam("http.connection.timeout", timeoutMillis)
                        .setParam("http.socket.timeout", timeoutMillis)))
                .addFilter(new AllureRestAssured())
                .addFilter(loggingFilter)
                .build();
    }

    public RawResponse execute(String method, String path, Object[] pathParams, String jsonBody) {
        RequestSpecification request = RestAssured.given().spec(spec);
        if (jsonBody != null) {
            request = request.contentType(ContentType.JSON).body(jsonBody);
        }

        Response response = switch (method) {
            case "GET" -> request.get(path, pathParams);
            case "POST" -> request.post(path, pathParams);
            case "PUT" -> request.put(path, pathParams);
            case "DELETE" -> request.delete(path, pathParams);
            default -> throw new IllegalStateException("Unsupported HTTP method: " + method);
        };

        return new RawResponse(response.statusCode(), headerMap(response), response.asString());
    }

    LoggingFilter loggingFilter() {
        return loggingFilter;
    }

    RequestSpecification requestSpecification() {
        return spec;
    }

    private static Map<String, String> headerMap(Response response) {
        Map<String, String> headers = new LinkedHashMap<>();
        response.headers().forEach(header -> headers.put(header.getName(), header.getValue()));
        return headers;
    }
}
