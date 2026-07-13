package dev.qacommons.ui;

import java.util.function.Function;

/**
 * UI-only config (headed/headless), deliberately separate from {@code
 * core}'s {@code QaConfig} - browser headedness has nothing to do with API
 * config, and {@code core} doesn't need to know {@code ui} exists.
 */
public record UiConfig(boolean headed) {

    private static final String HEADED_ENV = "QA_UI_HEADED";

    public static UiConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    public static UiConfig fromEnv(Function<String, String> lookup) {
        String value = lookup.apply(HEADED_ENV);
        boolean headed = value != null && Boolean.parseBoolean(value.trim());
        return new UiConfig(headed);
    }
}
