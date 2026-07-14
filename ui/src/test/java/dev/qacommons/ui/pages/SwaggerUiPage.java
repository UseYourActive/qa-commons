package dev.qacommons.ui.pages;

import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;
import dev.qacommons.ui.BasePage;

/**
 * Quarkus's built-in Swagger UI ({@code /q/swagger-ui}). Locators are
 * role-first per the ui-automation-patterns skill and verified against the
 * real rendered page (not assumed): tag section names are real {@code <a>}
 * elements (role {@code link}), each operation's clickable summary is a
 * real {@code <button aria-expanded>} (role {@code button}), and the
 * "Schema"/"Example Value" toggle after expanding is a real {@code role=tab}
 * pair. No CSS/XPath fallback was needed anywhere in this page object.
 */
public final class SwaggerUiPage extends BasePage {

    public SwaggerUiPage(Page page) {
        super(page);
    }

    public SwaggerUiPage open(String baseUrl) {
        page.navigate(baseUrl + "/q/swagger-ui");
        // Swagger UI is a client-rendered app - zero role=link elements
        // exist in the initial HTML shell (verified empirically), so
        // waiting for the first one is a reliable "the spec has actually
        // rendered" signal, not a guess at an arbitrary timeout.
        page.getByRole(AriaRole.LINK).first().waitFor();
        return this;
    }

    /**
     * Whether a tag section with exactly this name (e.g. "Notification
     * Delivery") is present - the contract-shape check the live tests
     * assert on, not a full endpoint-by-endpoint pixel comparison.
     *
     * <p>Uses {@code waitFor()}, not {@code count()}: {@code count()} is a
     * one-shot, non-retrying query - it happened to "work" in headless mode
     * purely because rendering was fast enough to already be done by the
     * time it ran, then failed under headed mode's slower real-window
     * rendering (found running the live suite with {@code QA_UI_HEADED=true}
     * during T7). {@code waitFor()} is Playwright's real auto-waiting
     * primitive, matching the skill's "no manual waits - built-in
     * auto-waiting covers existence/visibility" rule properly instead of by
     * accident.
     */
    public boolean hasEndpointGroup(String tagName) {
        try {
            page.getByRole(AriaRole.LINK, new Page.GetByRoleOptions().setName(tagName).setExact(true)).first().waitFor();
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }

    /**
     * Locates one operation by a fragment of its summary text (e.g. "Send a
     * notification") - the button's full accessible name also includes the
     * HTTP method and path, so this relies on {@code setName(String)}'s
     * substring-by-default matching rather than requiring the exact
     * concatenation.
     *
     * <p>Deliberately a plain {@code String}, not a compiled {@link
     * java.util.regex.Pattern}: verified empirically that Playwright's Java
     * bindings' {@code setName(Pattern)} does *not* do the substring match
     * its JS/Node equivalent does (a bare {@code Pattern.quote(...)} match
     * against this same button returned zero results), while the {@code
     * String} overload's documented substring-by-default behavior worked
     * correctly. Not assumed - directly reproduced and compared both ways
     * against the real page before choosing this.
     */
    public OperationRow operation(String summaryTextFragment) {
        return new OperationRow(page,
                page.getByRole(AriaRole.BUTTON, new Page.GetByRoleOptions().setName(summaryTextFragment)));
    }
}
