/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.strategy.named;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.rules.FixedRule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NamedStrategyTest {

    @Test
    void buildLabelRejectsUnderscoreParameters() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> NamedStrategy.buildLabel(NamedStrategyFixture.class, "fast", "slow_value", "u3"));

        assertEquals("Named strategy parameters cannot contain underscores: parameters[1]", ex.getMessage());
    }

    @Test
    void buildLabelRejectsNullParameters() {
        assertThrows(NullPointerException.class,
                () -> NamedStrategy.buildLabel(NamedStrategyFixture.class, "fast", null));
    }

    @Test
    void varargsConstructorStillAcceptedWithDelimiterFreeParameters() {
        var series = new MockBarSeriesBuilder().withData(1d).build();

        new NamedStrategyFixture(series, "1.0", "u3");
    }

    @Test
    void splitLabelBlankThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> NamedStrategy.splitLabel(""));
        assertEquals("Named strategy label cannot be blank", ex.getMessage());

        assertThrows(IllegalArgumentException.class, () -> NamedStrategy.splitLabel("   "));
    }

    @Test
    void splitLabelLeadingUnderscoreProducesEmptyFirstToken() {
        var tokens = NamedStrategy.splitLabel("_param");
        assertEquals(2, tokens.size());
        assertEquals("", tokens.get(0));
        assertEquals("param", tokens.get(1));
    }

    @Test
    void splitLabelTrailingUnderscoreProducesEmptyLastToken() {
        var tokens = NamedStrategy.splitLabel("StrategyName_");
        assertEquals(2, tokens.size());
        assertEquals("StrategyName", tokens.get(0));
        assertEquals("", tokens.get(1));
    }

    @Test
    void splitLabelNullThrows() {
        assertThrows(NullPointerException.class, () -> NamedStrategy.splitLabel(null));
    }

    @AfterEach
    void tearDown() {
        // Clean up test fixtures after each test
        NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);
    }

    @Test
    void unregisterRegisteredStrategyReturnsTrue() {
        NamedStrategy.registerImplementation(TestUnregisterStrategy.class);

        boolean result = NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);

        assertTrue(result);
    }

    @Test
    void unregisterNonRegisteredStrategyReturnsFalse() {
        // Ensure it's not registered
        NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);

        boolean result = NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);

        assertFalse(result);
    }

    @Test
    void unregisterRemovesStrategyFromLookup() {
        NamedStrategy.registerImplementation(TestUnregisterStrategy.class);
        assertTrue(NamedStrategy.lookup("TestUnregisterStrategy").isPresent());

        NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);

        assertFalse(NamedStrategy.lookup("TestUnregisterStrategy").isPresent());
    }

    @Test
    void unregisterAllowsReRegistration() {
        NamedStrategy.registerImplementation(TestUnregisterStrategy.class);
        NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);

        // Should be able to register again
        NamedStrategy.registerImplementation(TestUnregisterStrategy.class);
        assertTrue(NamedStrategy.lookup("TestUnregisterStrategy").isPresent());
    }

    @Test
    void unregisterWithNullThrowsNullPointerException() {
        assertThrows(NullPointerException.class, () -> NamedStrategy.unregisterImplementation(null));
    }

    @Test
    void unregisterOnlyRemovesExactMatch() {
        NamedStrategy.registerImplementation(TestUnregisterStrategy.class);
        assertTrue(NamedStrategy.lookup("TestUnregisterStrategy").isPresent());

        // Unregister should only remove if it's the exact same class
        boolean result = NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class);
        assertTrue(result);
        assertFalse(NamedStrategy.lookup("TestUnregisterStrategy").isPresent());
    }

    @Test
    void unregisterMultipleTimesReturnsFalseAfterFirst() {
        NamedStrategy.registerImplementation(TestUnregisterStrategy.class);

        assertTrue(NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class));
        assertFalse(NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class));
        assertFalse(NamedStrategy.unregisterImplementation(TestUnregisterStrategy.class));
    }

    /**
     * Test fixture strategy for testing unregister functionality. This class does
     * NOT have a static initializer to avoid auto-registration.
     */
    private static final class TestUnregisterStrategy extends NamedStrategy {

        private TestUnregisterStrategy(BarSeries series) {
            super("TestUnregisterStrategy", new FixedRule(1), new FixedRule(2), 0);
        }

        public TestUnregisterStrategy(BarSeries series, String... parameters) {
            this(series);
        }
    }
}