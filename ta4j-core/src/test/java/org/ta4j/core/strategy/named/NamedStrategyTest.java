/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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