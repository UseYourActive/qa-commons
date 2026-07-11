package dev.qacommons.core.testdata;

import java.util.Random;
import net.datafaker.Faker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Not thread-safe: each instance wraps a single {@link Random}. Construct one
 * instance per test (or per thread) - never share an instance across threads.
 */
public class SeededFaker {

    private static final Logger LOGGER = LoggerFactory.getLogger(SeededFaker.class);

    protected final Faker faker;

    protected SeededFaker(long seed) {
        this.faker = new Faker(new Random(seed));
        LOGGER.info("{} initialized with seed={}", getClass().getSimpleName(), seed);
    }
}
