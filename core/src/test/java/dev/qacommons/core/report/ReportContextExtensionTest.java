package dev.qacommons.core.report;

import static org.assertj.core.api.Assertions.assertThat;

import dev.qacommons.core.config.QaConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReportContextExtensionTest {

    @Test
    void beforeEach_attachesSeedAndBaseUrlAsParameters() {
        QaConfig config = new QaConfig("https://staging.example.com", 42L, Duration.ofSeconds(5));
        List<String> parameters = new ArrayList<>();
        Reporter recordingReporter = new Reporter() {
            @Override
            public void parameter(String name, String value) {
                parameters.add(name + "=" + value);
            }

            @Override
            public void attachment(String name, String mimeType, Path file) {
                throw new UnsupportedOperationException("not exercised by this extension");
            }
        };

        ReportContextExtension extension = new ReportContextExtension(() -> config, () -> recordingReporter);
        // beforeEach never reads the ExtensionContext - null is safe here.
        extension.beforeEach(null);

        assertThat(parameters).containsExactlyInAnyOrder("datafakerSeed=42", "baseUrl=https://staging.example.com");
    }
}
