package dev.qacommons.template.testdata;

import dev.qacommons.core.testdata.SeededFaker;
import dev.qacommons.template.model.CreateNotificationRequest;
import dev.qacommons.template.model.NotificationChannel;

public final class NotificationRequests extends SeededFaker {

    public NotificationRequests(long seed) {
        super(seed);
    }

    public CreateNotificationRequest valid() {
        return new CreateNotificationRequest(
                NotificationChannel.EMAIL,
                faker.internet().emailAddress(),
                null,
                faker.lorem().sentence(),
                null);
    }

    public CreateNotificationRequest missingRecipient() {
        CreateNotificationRequest base = valid();
        return new CreateNotificationRequest(base.channel(), null, base.templateName(), base.message(), base.data());
    }
}
