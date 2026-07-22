/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class RoughVolatilityForecastStateTest extends AbstractIndicatorTest<RoughVolatilityForecastState, Num> {

    public RoughVolatilityForecastStateTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void stableStateNormalizesSpecializedValuesAndDefendsTermStructure() {
        ReturnMoments moments = moments(ReturnRepresentation.LOG);
        NumFactory otherFactory = DecimalNumFactory.getInstance(40);
        List<Num> input = new ArrayList<>(List.of(otherFactory.numOf(4), otherFactory.numOf(5)));

        RoughVolatilityForecastState state = RoughVolatilityForecastState.stable(moments, otherFactory.numOf(0.1),
                otherFactory.numOf(0.25), input);
        input.set(0, otherFactory.numOf(99));

        assertTrue(state.isStable());
        assertEquals(7, state.index());
        assertEquals(8, state.observationCount());
        assertEquals(moments.variance().getClass(), state.roughnessHurst().getClass());
        assertEquals(moments.variance().getClass(), state.volOfVol().getClass());
        assertEquals(moments.variance().getClass(), state.horizonVarianceForecasts().get(0).getClass());
        assertNumEquals(4, state.horizonVarianceForecasts().get(0));
        assertThrows(UnsupportedOperationException.class, () -> state.horizonVarianceForecasts().add(numFactory.one()));
    }

    @Test
    public void unstableStateRetainsObservationsAndUsesSpecializedUnavailableShape() {
        RoughVolatilityForecastState state = RoughVolatilityForecastState.unstable(7, 6);

        assertFalse(state.isStable());
        assertEquals(6, state.observationCount());
        assertTrue(state.roughnessHurst().isNaN());
        assertTrue(state.volOfVol().isNaN());
        assertTrue(state.horizonVarianceForecasts().isEmpty());
    }

    @Test
    public void stableValidationRejectsIncoherentSpecializedFields() {
        ReturnMoments moments = moments(ReturnRepresentation.LOG);

        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0), numOf(1), List.of(numOf(4))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0.5), numOf(1), List.of(numOf(4))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, NaN.NaN, numOf(1), List.of(numOf(4))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0.1), numOf(-1), List.of(numOf(4))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0.1), numOf(1), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0.1), numOf(1), List.of(numOf(-1))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0.1), numOf(1), List.of(numOf(3))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(moments, numOf(0.1), numOf(1), List.of(numOf(4), numOf(3))));
    }

    @Test
    public void lifecycleAndRepresentationMustRemainCoherent() {
        ReturnMoments unstable = ReturnMoments.unstable(7, 6, ReturnRepresentation.LOG);

        assertThrows(IllegalArgumentException.class,
                () -> new RoughVolatilityForecastState(unstable, numOf(0.1), NaN.NaN, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new RoughVolatilityForecastState(unstable, NaN.NaN, NaN.NaN, List.of(numOf(1))));
        assertThrows(IllegalArgumentException.class,
                () -> new RoughVolatilityForecastState(moments(ReturnRepresentation.DECIMAL), numOf(0.1), numOf(1),
                        List.of(numOf(1))));
        assertThrows(IllegalArgumentException.class,
                () -> RoughVolatilityForecastState.stable(unstable, numOf(0.1), numOf(1), List.of(numOf(1))));
    }

    private ReturnMoments moments(ReturnRepresentation representation) {
        return ReturnMoments.stable(7, 8, representation, numOf(0.01), numOf(0), numOf(4));
    }
}
