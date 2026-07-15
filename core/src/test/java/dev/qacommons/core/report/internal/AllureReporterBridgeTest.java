package dev.qacommons.core.report.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import dev.qacommons.core.report.Reporter;
import io.qameta.allure.Allure;
import io.qameta.allure.AllureLifecycle;
import io.qameta.allure.AllureResultsWriter;
import io.qameta.allure.model.Parameter;
import io.qameta.allure.model.TestResult;
import io.qameta.allure.model.TestResultContainer;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Proves the reflective bridge actually reaches Allure's real API - not just
 * that it compiles. Runs a real {@link AllureLifecycle} test-case, backed by
 * an in-memory {@link AllureResultsWriter}, and inspects what actually got
 * written.
 */
class AllureReporterBridgeTest {

    @Test
    void fromClasspath_returnsAllureBridgeWhenAllureIsPresent() {
        assertThat(Reporter.fromClasspath()).isInstanceOf(AllureReporterBridge.class);
    }

    @Test
    void parameterAndAttachment_reachRealAllureApi(@TempDir Path tempDir) throws IOException {
        List<TestResult> written = new ArrayList<>();
        Map<String, byte[]> attachments = new HashMap<>();
        AllureLifecycle lifecycle = new AllureLifecycle(new AllureResultsWriter() {
            @Override
            public void write(TestResult testResult) {
                written.add(testResult);
            }

            @Override
            public void write(TestResultContainer testResultContainer) {
                // not needed for this assertion
            }

            @Override
            public void write(String source, InputStream attachmentContent) {
                try {
                    attachments.put(source, attachmentContent.readAllBytes());
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        });

        AllureLifecycle previous = Allure.getLifecycle();
        Allure.setLifecycle(lifecycle);
        try {
            String uuid = UUID.randomUUID().toString();
            lifecycle.scheduleTestCase(new TestResult().setUuid(uuid).setName("fake-test-for-bridge-assertion"));
            lifecycle.startTestCase(uuid);

            Reporter reporter = Reporter.fromClasspath();
            reporter.parameter("datafakerSeed", "42");

            Path screenshot = tempDir.resolve("screenshot.png");
            Files.write(screenshot, new byte[] {1, 2, 3});
            reporter.attachment("screenshot", "image/png", screenshot);

            lifecycle.stopTestCase(uuid);
            lifecycle.writeTestCase(uuid);
        } finally {
            Allure.setLifecycle(previous);
        }

        assertThat(written).hasSize(1);
        assertThat(written.get(0).getParameters())
                .extracting(Parameter::getName, Parameter::getValue)
                .contains(tuple("datafakerSeed", "42"));
        assertThat(attachments).hasSize(1);
        assertThat(attachments.values().iterator().next()).containsExactly(1, 2, 3);
    }

    @Test
    void attachment_missingFileLogsWarningRatherThanThrowing(@TempDir Path tempDir) {
        Reporter reporter = new AllureReporterBridge();

        reporter.attachment("missing", "image/png", tempDir.resolve("does-not-exist.png"));
        // No exception - a broken report attachment must never fail the test.
    }
}
