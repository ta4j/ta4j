/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.num;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.math.MathContext;
import java.math.RoundingMode;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DecimalNumPrecisionConfigurationTest {

    @AfterEach
    void restoreDefaults() {
        DecimalNum.resetDefaultPrecision();
    }

    @Test
    void configureDefaultPrecisionUpdatesValueOf() {
        DecimalNum.configureDefaultPrecision(18);

        final var num = DecimalNum.valueOf("1.2345");

        assertEquals(18, num.getMathContext().getPrecision());
    }

    @Test
    void configureDefaultMathContextUpdatesRoundingMode() {
        final var mathContext = new MathContext(12, RoundingMode.DOWN);
        DecimalNum.configureDefaultMathContext(mathContext);

        final var num = DecimalNum.valueOf("1.23456789");

        assertEquals(RoundingMode.DOWN, num.getMathContext().getRoundingMode());
        assertEquals(12, num.getMathContext().getPrecision());
    }

    @Test
    void factoryReflectsConfiguredPrecision() {
        DecimalNum.configureDefaultPrecision(22);

        final var factory = DecimalNumFactory.getInstance();
        final var num = (DecimalNum) factory.numOf("3.14159");

        assertEquals(22, num.getMathContext().getPrecision());
        assertEquals(22, ((DecimalNum) factory.one()).getMathContext().getPrecision());
        assertEquals(22, ((DecimalNum) factory.hundred()).getMathContext().getPrecision());
    }

    @Test
    void factoryWithExplicitContextUsesPrecisionForConstants() {
        final var customContext = new MathContext(28, RoundingMode.HALF_EVEN);
        final var factory = DecimalNumFactory.getInstance(customContext);

        final var num = (DecimalNum) factory.numOf("2.718281828");

        assertEquals(customContext, num.getMathContext());
        assertEquals(customContext, ((DecimalNum) factory.minusOne()).getMathContext());
        assertEquals(customContext, ((DecimalNum) factory.thousand()).getMathContext());
    }

    @Test
    void configuringInvalidPrecisionFails() {
        assertThrows(IllegalArgumentException.class, () -> DecimalNum.configureDefaultPrecision(0));
    }
}
