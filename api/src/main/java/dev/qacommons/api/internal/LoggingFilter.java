package dev.qacommons.api.internal;

import io.restassured.filter.Filter;
import io.restassured.filter.FilterContext;
import io.restassured.response.Response;
import io.restassured.specification.FilterableRequestSpecification;
import io.restassured.specification.FilterableResponseSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not thread-safe by design: one instance is created per {@link HttpEngine}
 * (and so per {@code Endpoint}) in its constructor and added to that
 * instance's request specification only - never shared or static.
 */
final class LoggingFilter implements Filter {

    private static final Logger LOGGER = LoggerFactory.getLogger(LoggingFilter.class);

    @Override
    public Response filter(FilterableRequestSpecification requestSpec, FilterableResponseSpecification responseSpec,
            FilterContext ctx) {
        LOGGER.debug("--> {} {}", requestSpec.getMethod(), requestSpec.getURI());
        Response response = ctx.next(requestSpec, responseSpec);
        LOGGER.debug("<-- {} {} status={}", requestSpec.getMethod(), requestSpec.getURI(), response.statusCode());
        return response;
    }
}
