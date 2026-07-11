package dev.qacommons.core.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

class QaConfigTest {

    @Test
    void fromEnv_usesDefaultsWhenNothingSet() {
        QaConfig config = QaConfig.fromEnv(key -> null);

        assertThat(config.baseUrl()).isEqualTo("http://localhost:8080");
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofMillis(10_000));
        assertThat(config.datafakerSeed()).isPositive();
    }

    @Test
    void fromEnv_readsOverridesFromLookup() {
        Map<String, String> env = Map.of(
                "QA_BASE_URL", "https://staging.example.com",
                "QA_SEED", "42",
                "QA_REQUEST_TIMEOUT_MS", "5000");

        QaConfig config = QaConfig.fromEnv(env::get);

        assertThat(config.baseUrl()).isEqualTo("https://staging.example.com");
        assertThat(config.datafakerSeed()).isEqualTo(42L);
        assertThat(config.requestTimeout()).isEqualTo(Duration.ofMillis(5_000));
    }

    @Test
    void fromEnv_blankBaseUrlFallsBackToDefault() {
        QaConfig config = QaConfig.fromEnv(key -> "QA_BASE_URL".equals(key) ? "   " : null);

        assertThat(config.baseUrl()).isEqualTo("http://localhost:8080");
    }

    @Test
    void fromEnv_noArgOverloadReadsRealEnvironment() {
        QaConfig config = QaConfig.fromEnv();

        assertThat(config.baseUrl()).isNotBlank();
        assertThat(config.requestTimeout()).isPositive();
    }
}
