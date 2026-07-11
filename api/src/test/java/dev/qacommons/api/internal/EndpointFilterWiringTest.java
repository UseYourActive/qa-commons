package dev.qacommons.api.internal;

import static org.assertj.core.api.Assertions.assertThat;

import dev.qacommons.core.config.QaConfig;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Pins "no static global filters" (qa-framework-architecture): each
 * {@link HttpEngine} must build its own {@link LoggingFilter} and request
 * specification in its constructor, never reuse or accumulate state shared
 * with another instance.
 */
class EndpointFilterWiringTest {

    private static QaConfig testConfig() {
        return new QaConfig("http://localhost:0", 1L, Duration.ofSeconds(1));
    }

    @Test
    void separateInstances_haveIndependentLoggingFilters() {
        HttpEngine first = new HttpEngine(testConfig());
        HttpEngine second = new HttpEngine(testConfig());

        assertThat(first.loggingFilter()).isNotSameAs(second.loggingFilter());
    }

    @Test
    void separateInstances_haveIndependentRequestSpecifications() {
        HttpEngine first = new HttpEngine(testConfig());
        HttpEngine second = new HttpEngine(testConfig());

        assertThat(first.requestSpecification()).isNotSameAs(second.requestSpecification());
    }

    @Test
    void repeatedConstruction_neverReturnsTheSameFilterInstance() {
        LoggingFilter[] filters = new LoggingFilter[5];
        for (int i = 0; i < filters.length; i++) {
            filters[i] = new HttpEngine(testConfig()).loggingFilter();
        }

        assertThat(filters).doesNotHaveDuplicates();
    }
}
