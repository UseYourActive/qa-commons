package dev.qacommons.ui;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.Tracing;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.ExtensionContext.Store;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Owns the full Playwright lifecycle. One {@link Browser} per worker
 * <b>thread</b> (not one per JVM - see plan.md's amendment): Playwright's
 * Java client is documented as not thread-safe ("all its methods... are
 * expected to be called on the same thread where the Playwright object was
 * created"), confirmed the hard way during development when a single
 * globally-shared {@code Browser} produced real protocol-corruption errors
 * under this repo's own concurrent test config. Each thread lazily creates
 * its own {@link Playwright} + {@link Browser} on first use and reuses it
 * for every later test the same pooled thread picks up; every created
 * instance registers into a single process-wide registry, closed exactly
 * once at test-plan shutdown. One fresh {@link BrowserContext} + {@link
 * Page} per test (isolated, no cookie/storage bleed between tests). Tests
 * receive the {@link Page} as a method parameter, never a field - the one
 * framework primitive tests are allowed to touch directly, used only to
 * construct a page object.
 *
 * <p><b>Thread-safety contract for the current-page binding</b>: this class
 * owns a separate {@code ThreadLocal<Page>} that {@code UiSoftAssertions}
 * reads at soft-assertion-failure time. It is set exactly once per test in
 * {@link #beforeEach} and removed exactly once per test in {@link
 * #afterEach} via {@code ThreadLocal.remove()} - never {@code
 * set(null)}. JUnit 5's parallel execution runs on a pooled worker pool: a
 * set-null still leaves a live map entry keyed to that (reused) thread,
 * which is at best a memory leak and at worst stale data a later test on
 * the same pooled thread could misread. No class other than this one may
 * write to this binding.
 */
public final class PlaywrightExtension implements BeforeEachCallback, AfterEachCallback, ParameterResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(PlaywrightExtension.class);

    private static final Namespace REGISTRY_NAMESPACE = Namespace.create(PlaywrightExtension.class, "registry");
    private static final Namespace TEST_NAMESPACE = Namespace.create(PlaywrightExtension.class, "test");
    private static final String REGISTRY_KEY = "browserRegistry";
    private static final String CONTEXT_KEY = "browserContext";
    private static final String PAGE_KEY = "page";

    // Thread-confined per Playwright's own documented requirement - see
    // class Javadoc. One BrowserHandle per worker thread, not one per JVM.
    private static final ThreadLocal<BrowserHandle> THREAD_BROWSER = new ThreadLocal<>();

    private static final ThreadLocal<Page> CURRENT_PAGE = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_TEST_ID = new ThreadLocal<>();
    private static final AtomicInteger ARTIFACT_SEQUENCE = new AtomicInteger();

    static Page currentPage() {
        return CURRENT_PAGE.get();
    }

    /**
     * Builds a fresh, always-unique path under {@code target/<subdir>/} for
     * the current test (falling back to the current thread's id if no test
     * id is bound - e.g. called from outside a {@link PlaywrightExtension}
     * -managed test). A monotonic sequence number is always appended, not
     * just the test id: a single {@code @RepeatedTest} produces many
     * artifacts sharing one test id, and without the sequence they would
     * silently overwrite each other.
     */
    static Path newArtifactPath(String subdir, String suffix) {
        String testId = CURRENT_TEST_ID.get();
        if (testId == null) {
            testId = "unbound-thread-" + Thread.currentThread().threadId();
        }
        Path dir = Paths.get("target", subdir);
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to create artifact directory: " + dir, e);
        }
        return dir.resolve(testId + "-" + ARTIFACT_SEQUENCE.incrementAndGet() + suffix);
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        Browser browser;
        try {
            browser = threadBrowser(context);
        } catch (PlaywrightException e) {
            // Thrown by the BrowserHandle constructor (Playwright.create() /
            // BrowserType.launch()) when the Chromium binary isn't
            // installed. This runs before the test method body, so a
            // try/catch inside the test itself could never observe this -
            // the graceful skip has to live here, converted to a JUnit 5
            // assumption failure (TestAbortedException), not a hard error.
            Assumptions.assumeTrue(false,
                    "Playwright browsers not installed - run `mvn -pl ui exec:java@install-browsers` "
                            + "first. Cause: " + e.getMessage());
            return; // unreachable - assumeTrue(false, ...) always throws
        }
        BrowserContext browserContext = browser.newContext();
        browserContext.tracing().start(new Tracing.StartOptions()
                .setScreenshots(true)
                .setSnapshots(true)
                .setSources(true));
        Page page = browserContext.newPage();

        Store store = context.getStore(TEST_NAMESPACE);
        store.put(CONTEXT_KEY, browserContext);
        store.put(PAGE_KEY, page);
        CURRENT_PAGE.set(page);
        CURRENT_TEST_ID.set(context.getRequiredTestClass().getSimpleName() + "-"
                + context.getRequiredTestMethod().getName());
    }

    @Override
    public void afterEach(ExtensionContext context) {
        Store store = context.getStore(TEST_NAMESPACE);
        BrowserContext browserContext = store.get(CONTEXT_KEY, BrowserContext.class);
        Page page = store.get(PAGE_KEY, Page.class);
        if (browserContext == null) {
            // beforeEach aborted before creating one (e.g. the graceful
            // browsers-not-installed skip via Assumptions.assumeTrue) -
            // JUnit 5 still calls afterEach in that case for cleanup
            // symmetry, so this is a real, expected path, not a bug to
            // paper over with a null check deep in the try block below.
            return;
        }
        boolean failed = context.getExecutionException().isPresent();

        try {
            if (failed) {
                Path tracePath = newArtifactPath("playwright-traces", ".zip");
                browserContext.tracing().stop(new Tracing.StopOptions().setPath(tracePath));
                LOGGER.error("Test failed - trace saved to {}", tracePath.toAbsolutePath());

                Path screenshotPath = newArtifactPath("playwright-screenshots", ".png");
                page.screenshot(new Page.ScreenshotOptions().setPath(screenshotPath));
                LOGGER.error("Test failed - screenshot saved to {}", screenshotPath.toAbsolutePath());
            } else {
                browserContext.tracing().stop();
            }
        } finally {
            browserContext.close();
            CURRENT_PAGE.remove();
            CURRENT_TEST_ID.remove();
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return parameterContext.getParameter().getType() == Page.class;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) {
        return extensionContext.getStore(TEST_NAMESPACE).get(PAGE_KEY, Page.class);
    }

    private static Browser threadBrowser(ExtensionContext context) {
        BrowserHandle handle = THREAD_BROWSER.get();
        if (handle == null) {
            handle = new BrowserHandle();
            THREAD_BROWSER.set(handle);
            registry(context).register(handle);
        }
        return handle.browser;
    }

    private static BrowserRegistry registry(ExtensionContext context) {
        Store rootStore = context.getRoot().getStore(REGISTRY_NAMESPACE);
        return rootStore.getOrComputeIfAbsent(REGISTRY_KEY, key -> new BrowserRegistry(), BrowserRegistry.class);
    }

    private static final class BrowserHandle {
        private final Playwright playwright;
        private final Browser browser;

        BrowserHandle() {
            // PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1 - without this, a missing
            // browser executable makes Playwright silently auto-download it
            // (multi-minute network operation) instead of throwing, which
            // would turn a supposedly-fast local unit test into an
            // unannounced background download on a fresh clone. Verified
            // empirically during T2: without this env var, hiding the
            // installed browsers made the "unit" test suite take 3 minutes
            // instead of ~4 seconds, silently re-downloading everything
            // rather than skipping - exactly the surprise the mission's
            // "documented AND automated sensibly, not assumed present"
            // requirement rules out.
            this.playwright = Playwright.create(new Playwright.CreateOptions()
                    .setEnv(Map.of("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1")));
            this.browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                    .setHeadless(!UiConfig.fromEnv().headed()));
        }

        void close() {
            browser.close();
            playwright.close();
        }
    }

    /**
     * Collects every per-thread {@link BrowserHandle} ever created across
     * the whole test plan and closes them all exactly once, at test-plan
     * shutdown - registered as a plain {@link AutoCloseable} in the root
     * store, which JUnit 5 (5.13+) auto-detects and closes without a
     * separate marker interface ({@code Store.CloseableResource} is
     * deprecated as of 5.13 for exactly this reason - confirmed against
     * the real jar). Triggers regardless of which thread happens to be the
     * one that closes the root context. Deliberately not a per-class
     * {@code AfterAllCallback} teardown (Playwright's own official JUnit
     * extension uses that, but only safely under class-level-only
     * parallelism - this repo's config also parallelizes at the method
     * level, where no single thread reliably "owns" a class's cleanup).
     */
    private static final class BrowserRegistry implements AutoCloseable {
        private final List<BrowserHandle> handles = Collections.synchronizedList(new ArrayList<>());

        void register(BrowserHandle handle) {
            handles.add(handle);
        }

        @Override
        public void close() {
            synchronized (handles) {
                for (BrowserHandle handle : handles) {
                    handle.close();
                }
            }
        }
    }
}
