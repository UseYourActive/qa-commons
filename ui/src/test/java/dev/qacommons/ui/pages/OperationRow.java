package dev.qacommons.ui.pages;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.TimeoutError;
import com.microsoft.playwright.options.AriaRole;

/**
 * One expandable operation row within {@link SwaggerUiPage} - a composed
 * fragment, not a subclass. Locators stay private; callers only ever see
 * business-named methods.
 */
public final class OperationRow {

    private final Page page;
    private final Locator summaryButton;

    OperationRow(Page page, Locator summaryButton) {
        this.page = page;
        this.summaryButton = summaryButton;
    }

    public OperationRow expand() {
        summaryButton.click();
        return this;
    }

    /**
     * Whether the "Schema" tab is showing for this (already expanded)
     * operation - the contract-shape check the live tests assert on.
     *
     * <p>Uses {@code waitFor()}, not {@code isVisible()}: {@code
     * isVisible()} is a one-shot, non-retrying query, the same anti-pattern
     * that broke {@link SwaggerUiPage#hasEndpointGroup} under headed mode -
     * see that method's Javadoc for how this was found.
     */
    public boolean schemaTabVisible() {
        try {
            page.getByRole(AriaRole.TAB, new Page.GetByRoleOptions().setName("Schema")).first().waitFor();
            return true;
        } catch (TimeoutError e) {
            return false;
        }
    }
}
