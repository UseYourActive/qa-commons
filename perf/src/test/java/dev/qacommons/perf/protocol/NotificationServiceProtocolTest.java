package dev.qacommons.perf.protocol;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Covers {@link NotificationServiceProtocol#resolveBaseUrl} only, via its
 * lookup/probe seam - {@code httpProtocol()} itself touches Gatling's {@code
 * http} DSL singleton, which throws "Simulations can't be instantiated
 * directly but only by Gatling" outside an actual Gatling-run Simulation, so
 * it isn't unit-testable here and is exercised for real by SmokeSimulation.
 */

class NotificationServiceProtocolTest {

    @Test
    void resolveBaseUrl_envSet_usesItVerbatim_noProbeCalled() {
        Map<String, String> env = Map.of("QA_BASE_URL", "https://staging.example.com");

        String baseUrl = NotificationServiceProtocol.resolveBaseUrl(
                env::get, url -> {
                    throw new AssertionError("reachability probe must not be called when QA_BASE_URL is set");
                });

        assertThat(baseUrl).isEqualTo("https://staging.example.com");
    }

    @Test
    void resolveBaseUrl_envUnset_localhostReachable_fallsBackToLocalhostDefault() {
        String baseUrl = NotificationServiceProtocol.resolveBaseUrl(key -> null, url -> true);

        assertThat(baseUrl).isEqualTo("http://localhost:8080");
    }

    @Test
    void resolveBaseUrl_blankEnv_treatedAsUnset() {
        Map<String, String> env = Map.of("QA_BASE_URL", "   ");

        String baseUrl = NotificationServiceProtocol.resolveBaseUrl(env::get, url -> true);

        assertThat(baseUrl).isEqualTo("http://localhost:8080");
    }

    @Test
    void resolveBaseUrl_envUnset_localhostUnreachable_refusesToRun() {
        assertThatThrownBy(() -> NotificationServiceProtocol.resolveBaseUrl(key -> null, url -> false))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("QA_BASE_URL")
                .hasMessageContaining("http://localhost:8080");
    }
}
