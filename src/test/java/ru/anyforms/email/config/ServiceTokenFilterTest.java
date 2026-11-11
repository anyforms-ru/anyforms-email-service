package ru.anyforms.email.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceTokenFilterTest {

    @Test
    void acceptsOnlyExactToken() {
        ServiceTokenFilter filter = new ServiceTokenFilter("secret");

        assertTrue(filter.matches("secret"));
        assertFalse(filter.matches("secret2"));
        assertFalse(filter.matches(""));
        assertFalse(filter.matches(null));
    }

    @Test
    void emptyConfiguredTokenClosesEverything() {
        ServiceTokenFilter filter = new ServiceTokenFilter("");

        assertFalse(filter.matches(""));
        assertFalse(filter.matches("anything"));
    }
}
