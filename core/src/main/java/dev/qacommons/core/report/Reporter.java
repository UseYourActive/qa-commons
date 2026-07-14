package dev.qacommons.core.report;

import dev.qacommons.core.report.internal.AllureReporterBridge;
import java.nio.file.Path;

/**
 * Thread-safe: implementations hold no mutable state - {@link Slf4jReporter}
 * is stateless, {@link AllureReporterBridge} just forwards to Allure's own
 * thread-safe lifecycle. A reporting failure never fails a test - unlike a
 * DB test oracle, this is a diagnostic side-channel, not the assertion
 * itself.
 */
public interface Reporter {

    /**
     * Attaches a named parameter (e.g. the datafaker seed, the target base
     * URL) to the current test in the report.
     */
    void parameter(String name, String value);

    /**
     * Attaches {@code file}'s contents to the current test in the report.
     */
    void attachment(String name, String mimeType, Path file);

    /**
     * {@link Slf4jReporter} (the zero-dependency default), or an
     * Allure-backed reporter if {@code io.qameta.allure.Allure} is present
     * on the caller's runtime classpath - detected fresh on every call, so
     * this never caches a decision made before a consumer's own classpath is
     * fully known.
     */
    static Reporter fromClasspath() {
        try {
            Class.forName("io.qameta.allure.Allure");
            return new AllureReporterBridge();
        } catch (ClassNotFoundException e) {
            return new Slf4jReporter();
        }
    }
}
