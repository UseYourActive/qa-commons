package dev.qacommons.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.microsoft.playwright.Page;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;

/**
 * Runs by default under {@code mvn clean verify} - no {@code @Tag("live")}.
 * Uses {@link PlaywrightExtension}, so gets the same real-browser /
 * graceful-skip-if-uninstalled behavior as {@link PlaywrightExtensionTest}.
 */
@ExtendWith(PlaywrightExtension.class)
class UiSoftAssertionsTest {

    private ListAppender<ILoggingEvent> logCapture;

    @BeforeEach
    void captureLogs() {
        logCapture = new ListAppender<>();
        logCapture.start();
        ((Logger) LoggerFactory.getLogger(UiSoftAssertions.class)).addAppender(logCapture);
    }

    @AfterEach
    void stopCapture() {
        ((Logger) LoggerFactory.getLogger(UiSoftAssertions.class)).detachAppender(logCapture);
    }

    @Test
    void screenshotsAtTheMomentOfEachSoftAssertionFailure_notJustAtTestEnd(Page page) throws IOException {
        page.navigate("data:text/html,<title>soft-assert-test</title>");
        UiSoftAssertions softly = new UiSoftAssertions();

        softly.assertThat(1).as("custom failure marker").isEqualTo(2);

        assertThat(logCapture.list)
                .as("the assertion's own message should reach the log output")
                .anyMatch(event -> event.getFormattedMessage().contains("custom failure marker"));

        assertThat(countScreenshotsFor("UiSoftAssertionsTest-screenshotsAtTheMomentOfEachSoftAssertionFailure"))
                .as("one screenshot should be written at the moment of the soft-assertion failure")
                .isEqualTo(1);

        // assertAll() re-throws the collected error, matching normal soft-
        // assertion semantics - consumed here rather than left to fail this
        // verification test itself.
        assertThatThrownBy(softly::assertAll).isInstanceOf(AssertionError.class);
    }

    private static long countScreenshotsFor(String testIdPrefix) throws IOException {
        Path dir = Paths.get("target", "playwright-screenshots");
        if (!Files.exists(dir)) {
            return 0;
        }
        long count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, testIdPrefix + "*.png")) {
            for (Path ignored : stream) {
                count++;
            }
        }
        return count;
    }
}
