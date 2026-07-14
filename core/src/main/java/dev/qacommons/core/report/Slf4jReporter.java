package dev.qacommons.core.report;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The zero-dependency default {@link Reporter}: every call just logs, no
 * report is produced. Thread-safe - holds no state.
 */
public final class Slf4jReporter implements Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Slf4jReporter.class);

    @Override
    public void parameter(String name, String value) {
        LOGGER.info("{}={}", name, value);
    }

    @Override
    public void attachment(String name, String mimeType, Path file) {
        LOGGER.info("{} ({}) saved to {}", name, mimeType, file.toAbsolutePath());
    }
}
