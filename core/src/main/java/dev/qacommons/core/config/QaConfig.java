package dev.qacommons.core.config;

import java.time.Duration;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Thread-safe: immutable record, safe to share across threads once constructed.
 */
public record QaConfig(String baseUrl, long datafakerSeed, Duration requestTimeout) {

    private static final Logger LOGGER = LoggerFactory.getLogger(QaConfig.class);

    private static final String BASE_URL_ENV = "QA_BASE_URL";
    private static final String SEED_ENV = "QA_SEED";
    private static final String REQUEST_TIMEOUT_MS_ENV = "QA_REQUEST_TIMEOUT_MS";

    private static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final long DEFAULT_REQUEST_TIMEOUT_MS = 10_000;

    public static QaConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    public static QaConfig fromEnv(Function<String, String> lookup) {
        String baseUrl = blankToNull(lookup.apply(BASE_URL_ENV));
        long seed = parseLongOrDefault(lookup.apply(SEED_ENV), System.currentTimeMillis());
        long timeoutMs = parseLongOrDefault(lookup.apply(REQUEST_TIMEOUT_MS_ENV), DEFAULT_REQUEST_TIMEOUT_MS);

        QaConfig config = new QaConfig(
                baseUrl != null ? baseUrl : DEFAULT_BASE_URL,
                seed,
                Duration.ofMillis(timeoutMs));

        LOGGER.info("QaConfig loaded: baseUrl={}, datafakerSeed={}, requestTimeout={}",
                config.baseUrl(), config.datafakerSeed(), config.requestTimeout());
        return config;
    }

    private static String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private static long parseLongOrDefault(String value, long defaultValue) {
        String trimmed = blankToNull(value);
        return trimmed != null ? Long.parseLong(trimmed.trim()) : defaultValue;
    }
}
