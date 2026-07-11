package dev.qacommons.core.testdata;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SeededFakerTest {

    private static final class TestFaker extends SeededFaker {
        TestFaker(long seed) {
            super(seed);
        }

        String nextName() {
            return faker.name().fullName();
        }
    }

    @Test
    void sameSeed_producesSameSequence() {
        TestFaker first = new TestFaker(42L);
        TestFaker second = new TestFaker(42L);

        assertThat(first.nextName()).isEqualTo(second.nextName());
    }

    @Test
    void differentSeeds_produceDifferentSequences() {
        TestFaker first = new TestFaker(1L);
        TestFaker second = new TestFaker(2L);

        assertThat(first.nextName()).isNotEqualTo(second.nextName());
    }

    @Test
    void construction_logsSeedAtInfo() {
        Logger logger = (Logger) LoggerFactory.getLogger(SeededFaker.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            new TestFaker(7L);
        } finally {
            logger.detachAppender(appender);
        }

        assertThat(appender.list).anySatisfy(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.INFO);
            assertThat(event.getFormattedMessage()).contains("7");
        });
    }
}
