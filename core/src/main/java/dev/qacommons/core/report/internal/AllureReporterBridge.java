package dev.qacommons.core.report.internal;

import dev.qacommons.core.report.Reporter;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The only place in {@code core} that ever names Allure - and even here,
 * only as a string ({@code "io.qameta.allure.Allure"}), never a compile-time
 * import, so {@code core}'s own {@code pom.xml} carries zero Allure
 * dependency, not even {@code optional}/{@code provided}. Only ever
 * constructed by {@link Reporter#fromClasspath()}, which already proved via
 * {@code Class.forName} that the class is present before calling this
 * constructor.
 *
 * <p>A reflective call failing <em>after</em> that point (unexpected version
 * skew) logs a WARN and the test continues rather than throwing - a broken
 * report attachment must never fail an otherwise-passing test, unlike a DB
 * oracle failure, which is the assertion itself and always fails loud.
 *
 * <p>Thread-safe: holds no state, every call resolves and invokes Allure's
 * own (thread-safe) static API fresh.
 */
public final class AllureReporterBridge implements Reporter {

    private static final Logger LOGGER = LoggerFactory.getLogger(AllureReporterBridge.class);
    private static final String ALLURE_CLASS = "io.qameta.allure.Allure";

    @Override
    public void parameter(String name, String value) {
        try {
            Class<?> allure = Class.forName(ALLURE_CLASS);
            Method parameter = allure.getMethod("parameter", String.class, Object.class);
            parameter.invoke(null, name, value);
        } catch (ReflectiveOperationException e) {
            LOGGER.warn("Failed to attach parameter {}={} to the Allure report", name, value, e);
        }
    }

    @Override
    public void attachment(String name, String mimeType, Path file) {
        try (InputStream content = Files.newInputStream(file)) {
            Class<?> allure = Class.forName(ALLURE_CLASS);
            Method addAttachment = allure.getMethod(
                    "addAttachment", String.class, String.class, InputStream.class, String.class);
            addAttachment.invoke(null, name, mimeType, content, extensionOf(file));
        } catch (ReflectiveOperationException | IOException e) {
            LOGGER.warn("Failed to attach {} ({}) to the Allure report", name, mimeType, e);
        }
    }

    private static String extensionOf(Path file) {
        String fileName = file.getFileName().toString();
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 ? fileName.substring(dot) : "";
    }
}
