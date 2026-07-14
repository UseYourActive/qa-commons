package dev.qacommons.ui;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/**
 * Deliberately has no {@code @ExtendWith(PlaywrightExtension.class)} - this
 * class's test methods run with no {@code Page} ever bound for their
 * thread, exercising the fail-loud-when-unbound contract without needing
 * to spawn a separate thread by hand.
 */
class UiSoftAssertionsUnboundTest {

    @Test
    void failsLoudWhenNoPageIsBoundForTheCurrentThread() {
        UiSoftAssertions softly = new UiSoftAssertions();

        assertThatThrownBy(() -> softly.assertThat(1).isEqualTo(2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PlaywrightExtension");
    }
}
