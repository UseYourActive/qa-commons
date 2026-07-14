package dev.qacommons.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UiConfigTest {

    @Test
    void headedDefaultsToFalseWhenEnvVarUnset() {
        UiConfig config = UiConfig.fromEnv(name -> null);

        assertThat(config.headed()).isFalse();
    }

    @Test
    void headedTrueWhenEnvVarSetToTrue() {
        UiConfig config = UiConfig.fromEnv(name -> "true");

        assertThat(config.headed()).isTrue();
    }

    @Test
    void headedFalseWhenEnvVarSetToSomethingElse() {
        UiConfig config = UiConfig.fromEnv(name -> "nope");

        assertThat(config.headed()).isFalse();
    }

    @Test
    void headedTrueIgnoresCaseAndSurroundingWhitespace() {
        UiConfig config = UiConfig.fromEnv(name -> " TRUE ");

        assertThat(config.headed()).isTrue();
    }
}
