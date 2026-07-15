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

public class OnlineChangePointForecastStateTest extends AbstractIndicatorTest<OnlineChangePointForecastState, Num> {

    public OnlineChangePointForecastStateTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void stableStateNormalizesAndDefendsOrderedPosteriorSummaries() {
        ReturnMoments moments = moments(ReturnRepresentation.LOG);
        NumFactory otherFactory = DecimalNumFactory.getInstance(40);
        List<RunLengthPosterior> input = new ArrayList<>(List.of(
                new RunLengthPosterior(8, otherFactory.numOf(0.7), otherFactory.numOf(1), otherFactory.numOf(4)),
                new RunLengthPosterior(2, otherFactory.numOf(0.2), otherFactory.numOf(0.5), otherFactory.numOf(5))));

        OnlineChangePointForecastState state = OnlineChangePointForecastState.stable(moments, otherFactory.numOf(0.25),
                8, input);
        input.clear();

        assertTrue(state.isStable());
        assertEquals(7, state.index());
        assertEquals(8, state.observationCount());
        assertEquals(moments.variance().getClass(), state.recentChangeProbability().getClass());
        assertEquals(moments.variance().getClass(), state.topRunLengths().get(0).probability().getClass());
        assertNumEquals(0.25, state.recentChangeProbability());
        assertEquals(8, state.mostLikelyRunLength());
        assertEquals(2, state.topRunLengths().size());
        assertThrows(UnsupportedOperationException.class,
                () -> state.topRunLengths().add(new RunLengthPosterior(1, numOf(0.1), numOf(0), numOf(1))));
    }

    @Test
    public void unstableStateRetainsObservationsAndUsesUnavailableSpecializedShape() {
        OnlineChangePointForecastState state = OnlineChangePointForecastState.unstable(7, 6);

        assertFalse(state.isStable());
        assertEquals(6, state.observationCount());
        assertTrue(state.recentChangeProbability().isNaN());
        assertEquals(-1, state.mostLikelyRunLength());
        assertTrue(state.topRunLengths().isEmpty());
    }

    @Test
    public void stableValidationRejectsIncoherentState() {
        ReturnMoments moments = moments(ReturnRepresentation.LOG);
        RunLengthPosterior map = posterior(8, 0.7, 1, 4);
        RunLengthPosterior second = posterior(2, 0.2, 0.5, 5);

        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(-0.1), 8, List.of(map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(1.1), 8, List.of(map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), -1, List.of(map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), 8, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), 2, List.of(map, second)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), 2, List.of(second, map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), 8, List.of(map, map)));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), 8,
                List.of(posterior(8, 0.7, 1, 4), posterior(2, 0.4, 0.5, 5))));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, numOf(0.2), 8, List.of(posterior(8, 0.7, 2, 4))));
    }

    @Test
    public void lifecycleRepresentationAndMapMomentContractsRemainCoherent() {
        ReturnMoments unstable = ReturnMoments.unstable(7, 6, ReturnRepresentation.LOG);
        ReturnMoments zeroDrift = ReturnMoments.stable(7, 8, ReturnRepresentation.LOG, numOf(1), numOf(0), numOf(4));

        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastState(unstable, numOf(0.1), -1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastState(unstable, NaN.NaN, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastState(moments(ReturnRepresentation.DECIMAL), numOf(0.1), 8,
                        List.of(posterior(8, 1, 1, 4))));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(unstable, numOf(0.1), 8, List.of(posterior(8, 1, 1, 4))));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(zeroDrift, numOf(0.1), 8, List.of(posterior(8, 1, 1, 4))));
    }

    private ReturnMoments moments(ReturnRepresentation representation) {
        return ReturnMoments.stable(7, 8, representation, numOf(1), numOf(1), numOf(4));
    }

    private RunLengthPosterior posterior(int runLength, double probability, double mean, double variance) {
        return new RunLengthPosterior(runLength, numOf(probability), numOf(mean), numOf(variance));
    }
}
