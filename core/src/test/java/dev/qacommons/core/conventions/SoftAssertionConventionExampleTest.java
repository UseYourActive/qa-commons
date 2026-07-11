package dev.qacommons.core.conventions;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Reference example for qa-commons' soft-assertion convention: use AssertJ's
 * built-in {@link SoftAssertionsExtension}, never a hand-rolled verification
 * wrapper. Copy this pattern wherever a test needs to assert several
 * independent facts about one result without stopping at the first failure.
 */
@ExtendWith(SoftAssertionsExtension.class)
class SoftAssertionConventionExampleTest {

    @InjectSoftAssertions
    SoftAssertions softly;

    private record Widget(String name, int quantity) {
    }

    @Test
    void allAssertionsAreCollectedBeforeFailing() {
        Widget widget = new Widget("bolt", 42);

        softly.assertThat(widget.name()).isEqualTo("bolt");
        softly.assertThat(widget.quantity()).isPositive();
    }
}
