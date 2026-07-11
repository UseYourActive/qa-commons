package dev.qacommons.core.json;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class JsonMapperFactoryTest {

    private record Sample(UUID id, Instant createdAt, String name) {
    }

    @Test
    void newMapper_roundTripsUuidAndInstant() throws Exception {
        ObjectMapper mapper = JsonMapperFactory.newMapper();
        Sample original = new Sample(UUID.randomUUID(), Instant.parse("2024-01-01T00:00:00Z"), "widget");

        String json = mapper.writeValueAsString(original);
        Sample roundTripped = mapper.readValue(json, Sample.class);

        assertThat(roundTripped).isEqualTo(original);
    }

    @Test
    void newMapper_ignoresUnknownProperties() throws Exception {
        ObjectMapper mapper = JsonMapperFactory.newMapper();

        Sample parsed = mapper.readValue(
                "{\"id\":\"" + UUID.randomUUID() + "\",\"createdAt\":\"2024-01-01T00:00:00Z\","
                        + "\"name\":\"widget\",\"unexpectedField\":\"ignored\"}",
                Sample.class);

        assertThat(parsed.name()).isEqualTo("widget");
    }

    @Test
    void newMapper_returnsIndependentInstances() {
        ObjectMapper first = JsonMapperFactory.newMapper();
        ObjectMapper second = JsonMapperFactory.newMapper();

        assertThat(first).isNotSameAs(second);

        first.enable(SerializationFeature.INDENT_OUTPUT);

        assertThat(second.isEnabled(SerializationFeature.INDENT_OUTPUT)).isFalse();
    }
}
