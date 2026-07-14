package dev.qacommons.ui;

import com.microsoft.playwright.Page;
import dev.qacommons.core.report.Reporter;
import java.nio.file.Path;
import org.assertj.core.api.SoftAssertions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Screenshots at the moment of each soft-assertion failure, carrying the
 * assertion's own message - not just at test end. Register with
 * {@code @InjectSoftAssertions UiSoftAssertions softly;} instead of plain
 * {@code SoftAssertions}, on a test class also extended with {@link
 * PlaywrightExtension}.
 *
 * <p>Thread-safety: reads the current test's {@link Page} from {@link
 * PlaywrightExtension}'s thread-local binding lazily, at actual failure
 * time - not at construction time, since AssertJ constructs this instance
 * before any {@code beforeEach} callback runs (see {@link
 * PlaywrightExtension}'s class Javadoc for the full ownership contract of
 * that binding). If no {@code Page} is bound when a soft assertion fails,
 * this throws {@link IllegalStateException} rather than silently skipping
 * the screenshot - a broken wiring should look like a broken wiring, not
 * "zero failures ever needed a screenshot."
 */
public class UiSoftAssertions extends SoftAssertions {

    private static final Logger LOGGER = LoggerFactory.getLogger(UiSoftAssertions.class);

    public UiSoftAssertions() {
        addAfterAssertionErrorCollected(this::onSoftAssertionFailed);
    }

    private void onSoftAssertionFailed(AssertionError error) {
        Page page = PlaywrightExtension.currentPage();
        if (page == null) {
            throw new IllegalStateException(
                    "UiSoftAssertions requires PlaywrightExtension to be registered on this test class "
                            + "(no Page bound for the current thread) - add "
                            + "@ExtendWith(PlaywrightExtension.class).",
                    error);
        }
        try {
            Path path = PlaywrightExtension.newArtifactPath("playwright-screenshots", ".png");
            page.screenshot(new Page.ScreenshotOptions().setPath(path));
            LOGGER.error("Soft assertion failed: {} - screenshot saved to {}",
                    error.getMessage(), path.toAbsolutePath());
            Reporter.fromClasspath().attachment("screenshot", "image/png", path);
        } catch (RuntimeException screenshotFailure) {
            // Never let a diagnostics failure mask the assertion failure it
            // was trying to diagnose.
            LOGGER.error("Soft assertion failed: {} - screenshot could not be captured: {}",
                    error.getMessage(), screenshotFailure.getMessage(), screenshotFailure);
        }
    }
}
