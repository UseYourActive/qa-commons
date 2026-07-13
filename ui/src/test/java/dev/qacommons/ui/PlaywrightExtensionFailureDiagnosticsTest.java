package dev.qacommons.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import com.microsoft.playwright.Page;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;

/**
 * Runs by default under {@code mvn clean verify} - no {@code @Tag("live")}.
 * Verifies {@link PlaywrightExtension}'s retain-on-failure behavior by
 * programmatically launching two small, deliberately isolated test classes
 * ({@link FailingCase}, {@link PassingCase}) via the JUnit Platform
 * Launcher, then inspecting what landed on disk - a real end-to-end check
 * rather than calling {@code afterEach} directly with hand-built mocks.
 */
class PlaywrightExtensionFailureDiagnosticsTest {

    @ExtendWith(PlaywrightExtension.class)
    static class FailingCase {
        @Test
        void hardFailure(Page page) {
            page.navigate("data:text/html,<title>hard-failure-case</title>");
            throw new RuntimeException("deliberate hard failure for diagnostics test");
        }
    }

    @ExtendWith(PlaywrightExtension.class)
    static class PassingCase {
        @Test
        void passes(Page page) {
            page.navigate("data:text/html,<title>passing-case</title>");
        }
    }

    @Test
    void hardFailure_capturesTraceAndScreenshot() throws IOException {
        TestExecutionSummary summary = runClass(FailingCase.class);

        assertThat(summary.getTotalFailureCount()).isEqualTo(1);
        assertThat(countArtifactsFor("playwright-traces", "FailingCase-hardFailure", ".zip")).isEqualTo(1);
        assertThat(countArtifactsFor("playwright-screenshots", "FailingCase-hardFailure", ".png")).isEqualTo(1);
    }

    @Test
    void passingTest_capturesNoArtifacts() throws IOException {
        TestExecutionSummary summary = runClass(PassingCase.class);

        assertThat(summary.getTotalFailureCount()).isEqualTo(0);
        assertThat(countArtifactsFor("playwright-traces", "PassingCase-passes", ".zip")).isEqualTo(0);
        assertThat(countArtifactsFor("playwright-screenshots", "PassingCase-passes", ".png")).isEqualTo(0);
    }

    private static TestExecutionSummary runClass(Class<?> testClass) {
        LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                .selectors(selectClass(testClass))
                .build();
        Launcher launcher = LauncherFactory.create();
        SummaryGeneratingListener listener = new SummaryGeneratingListener();
        launcher.registerTestExecutionListeners(listener);
        launcher.execute(request);
        return listener.getSummary();
    }

    private static long countArtifactsFor(String subdir, String testIdPrefix, String suffix) throws IOException {
        Path dir = Paths.get("target", subdir);
        if (!Files.exists(dir)) {
            return 0;
        }
        long count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, testIdPrefix + "*" + suffix)) {
            for (Path ignored : stream) {
                count++;
            }
        }
        return count;
    }
}
