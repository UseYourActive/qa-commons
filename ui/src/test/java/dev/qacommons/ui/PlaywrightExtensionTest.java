package dev.qacommons.ui;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Runs by default under {@code mvn clean verify} - no {@code @Tag("live")}.
 * Drives a real headless Chromium instance against {@code data:} URLs only,
 * so it needs no external service. If Chromium isn't installed,
 * {@link PlaywrightExtension#beforeEach} converts the resulting
 * {@code PlaywrightException} into a JUnit 5 assumption failure and these
 * tests are reported skipped, not failed - see plan.md.
 */
@ExtendWith(PlaywrightExtension.class)
class PlaywrightExtensionTest {

    @Test
    void injectsANonNullPage(Page page) {
        assertThat(page).isNotNull();
    }

    @Test
    void firstTest_seesOnlyItsOwnNavigatedContent(Page page) {
        page.navigate("data:text/html,<title>first</title><h1>first page</h1>");
        assertThat(page.title()).isEqualTo("first");
    }

    @Test
    void secondTest_seesOnlyItsOwnNavigatedContent(Page page) {
        page.navigate("data:text/html,<title>second</title><h1>second page</h1>");
        assertThat(page.title()).isEqualTo("second");
    }

    /**
     * Under the concurrent config in junit-platform.properties, repetitions
     * run on multiple pooled threads. If the extension ever regressed to
     * sharing one Page/BrowserContext instead of one per test, this would
     * flake under real concurrency - each repetition asserts it only ever
     * observes the title *it itself* navigated to, never a value left by a
     * repetition running on another thread at the same time.
     */
    @RepeatedTest(8)
    void concurrentRepetitions_neverObserveAnotherTestsNavigation(Page page) {
        String uniqueTitle = "run-" + System.nanoTime();
        page.navigate("data:text/html,<title>" + uniqueTitle + "</title>");
        assertThat(page.title()).isEqualTo(uniqueTitle);
    }
}
