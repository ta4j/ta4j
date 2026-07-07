/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ReturnForecastStateTest extends AbstractIndicatorTest<ReturnForecastState, Num> {

    public ReturnForecastStateTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void unstableStateIsMarkedUnstable() {
        ReturnForecastState state = ReturnForecastState.unstable(1);

        assertFalse(state.isStable());
        assertTrue(state.mean().isNaN());
    }

    @Test
    public void validationRejectsInvalidShape() {
        assertThrows(IllegalArgumentException.class,
                () -> new ReturnForecastState(1, 0, true, numOf(0), numOf(0), numOf(0), numOf(0)));
        assertThrows(IllegalArgumentException.class,
                () -> new ReturnForecastState(1, 1, false, numOf(0), numOf(0), numOf(0), numOf(0)));
    }
}
