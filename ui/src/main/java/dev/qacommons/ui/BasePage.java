package dev.qacommons.ui;

import com.microsoft.playwright.Page;

/**
 * Base class for one page/screen/major component. Holds only the
 * cross-cutting {@link Page} handle - no generic action methods (the old
 * {@code BasePage.click(locator)} wrapper style is banned). Subclasses
 * expose business-named methods only; locators stay private to each page
 * object.
 */
public abstract class BasePage {

    protected final Page page;

    protected BasePage(Page page) {
        this.page = page;
    }
}
