package dev.qacommons.core.report;

import dev.qacommons.core.config.QaConfig;
import java.util.function.Supplier;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Attaches the current test's datafaker seed and target base URL to the
 * report, for reproducibility from the report alone. Add via
 * {@code @ExtendWith(ReportContextExtension.class)} on any live test class.
 *
 * <p>Thread-safe: holds no mutable state - {@code QaConfig.fromEnv()} and
 * {@link Reporter#fromClasspath()} are called fresh in {@link #beforeEach},
 * the same pattern every live test already uses to build its own
 * {@code QaConfig}.
 */
public final class ReportContextExtension implements BeforeEachCallback {

    private final Supplier<QaConfig> configSupplier;
    private final Supplier<Reporter> reporterSupplier;

    public ReportContextExtension() {
        this(QaConfig::fromEnv, Reporter::fromClasspath);
    }

    // Package-visible seam: lets tests inject a test-double config/reporter
    // instead of touching real env vars or Allure.
    ReportContextExtension(Supplier<QaConfig> configSupplier, Supplier<Reporter> reporterSupplier) {
        this.configSupplier = configSupplier;
        this.reporterSupplier = reporterSupplier;
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        QaConfig config = configSupplier.get();
        Reporter reporter = reporterSupplier.get();
        reporter.parameter("datafakerSeed", String.valueOf(config.datafakerSeed()));
        reporter.parameter("baseUrl", config.baseUrl());
    }
}
