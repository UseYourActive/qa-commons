package dev.qacommons.ui.tests;

import static org.assertj.core.api.Assertions.assertThat;

import com.microsoft.playwright.Page;
import dev.qacommons.core.config.QaConfig;
import dev.qacommons.core.report.ReportContextExtension;
import dev.qacommons.ui.PlaywrightExtension;
import dev.qacommons.ui.pages.OperationRow;
import dev.qacommons.ui.pages.SwaggerUiPage;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Requires the real notification service running locally - see the root
 * README for how to start it. Run with {@code mvn -pl ui -am test
 * -DrunLive=true}; excluded from the default {@code mvn clean verify}.
 * Asserts contract shape (endpoint group presence, schema tab visibility),
 * not pixel details that would break on a routine Quarkus/swagger-ui
 * version bump.
 */
@Tag("live")
@ExtendWith({PlaywrightExtension.class, ReportContextExtension.class})
class SwaggerUiTest {

    private String baseUrl() {
        return QaConfig.fromEnv().baseUrl();
    }

    @Test
    void loadingSwaggerUi_listsNotificationDeliveryEndpointGroup(Page page) {
        SwaggerUiPage swagger = new SwaggerUiPage(page).open(baseUrl());

        assertThat(swagger.hasEndpointGroup("Notification Delivery")).isTrue();
    }

    @Test
    void expandingSendNotificationOperation_showsSchema(Page page) {
        SwaggerUiPage swagger = new SwaggerUiPage(page).open(baseUrl());

        OperationRow sendOperation = swagger.operation("Send a notification").expand();

        assertThat(sendOperation.schemaTabVisible()).isTrue();
    }

    @Test
    void expandingListFailedNotificationsOperation_showsSchema(Page page) {
        SwaggerUiPage swagger = new SwaggerUiPage(page).open(baseUrl());

        OperationRow listFailedOperation = swagger.operation("List failed notifications").expand();

        assertThat(listFailedOperation.schemaTabVisible()).isTrue();
    }
}
