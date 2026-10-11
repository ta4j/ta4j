/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ReturnForecastStateTest extends AbstractIndicatorTest<ReturnForecastState, Num> {

    public ReturnForecastStateTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void minimalLifecycleAndPositiveObservationUnstableStateCompose() {
        ReturnForecastState state = ReturnForecastState.unstable(3, 20, ReturnRepresentation.LOG);
        ForecastState lifecycle = state;

        assertEquals(3, lifecycle.index());
        assertFalse(lifecycle.isStable());
        assertEquals(20, state.observationCount());
        assertTrue(state.mean().isNaN());
    }

    @Test
    public void varianceIsCanonicalAndVolatilityIsDerived() {
        ReturnForecastState state = ReturnForecastState.stable(3, 20, ReturnRepresentation.LOG, numOf(1), numOf(2),
                numOf(9));

        assertEquals(ReturnRepresentation.LOG, state.representation());
        assertEquals(0, state.volatility().compareTo(numOf(3)));
        assertEquals(state.variance().getClass(), state.volatility().getClass());
        if (state.variance() instanceof DecimalNum variance && state.volatility() instanceof DecimalNum volatility) {
            assertEquals(variance.getMathContext(), volatility.getMathContext());
        }
    }

    @Test
    public void validationRejectsInvalidMoments() {
        assertThrows(IllegalArgumentException.class,
                () -> ReturnMoments.stable(1, 0, ReturnRepresentation.LOG, numOf(0), numOf(0), numOf(0)));
        assertThrows(IllegalArgumentException.class,
                () -> ReturnMoments.stable(1, 1, ReturnRepresentation.LOG, NaN.NaN, numOf(0), numOf(0)));
        assertThrows(IllegalArgumentException.class,
                () -> ReturnMoments.stable(1, 1, ReturnRepresentation.LOG, numOf(0), numOf(0), numOf(-1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ReturnMoments(1, 1, false, ReturnRepresentation.LOG, numOf(0), NaN.NaN, NaN.NaN));
    }
}
