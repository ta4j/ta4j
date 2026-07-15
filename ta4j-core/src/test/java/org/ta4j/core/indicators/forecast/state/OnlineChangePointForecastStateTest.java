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
                new RunLengthPosterior(8, otherFactory.numOf(0.7), otherFactory.one(), otherFactory.numOf(4)),
                new RunLengthPosterior(2, otherFactory.numOf(0.2), otherFactory.numOf(0.5), otherFactory.numOf(5))));

        OnlineChangePointForecastState state = OnlineChangePointForecastState.stable(moments, 5,
                otherFactory.numOf(0.25), 8, input);
        input.clear();

        assertTrue(state.isStable());
        assertEquals(7, state.index());
        assertEquals(8, state.observationCount());
        assertEquals(moments.variance().getClass(), state.recentChangeProbability().getClass());
        assertEquals(moments.variance().getClass(), state.topRunLengths().get(0).probability().getClass());
        assertNumEquals(0.25, state.recentChangeProbability());
        assertEquals(5, state.recentChangeWindow());
        assertEquals(8, state.mostLikelyRunLength());
        assertEquals(2, state.topRunLengths().size());
        assertThrows(UnsupportedOperationException.class,
                () -> state.topRunLengths().add(new RunLengthPosterior(1, numOf(0.1), numOf(0), numOf(1))));
    }

    @Test
    public void unstableStateRetainsObservationsAndUsesUnavailableSpecializedShape() {
        OnlineChangePointForecastState state = OnlineChangePointForecastState.unstable(7, 6, 5);

        assertFalse(state.isStable());
        assertEquals(6, state.observationCount());
        assertEquals(5, state.recentChangeWindow());
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
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(-0.1), 8, List.of(map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(1.1), 8, List.of(map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2), -1, List.of(map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2), 8, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2), 2, List.of(map, second)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2), 2, List.of(second, map)));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2), 8, List.of(map, map)));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2),
                9, List.of(posterior(9, 0.7, 1, 4))));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2),
                8, List.of(posterior(8, 0.7, 1, 4), posterior(2, 0.4, 0.5, 5))));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2),
                8, List.of(posterior(8, 0.7, 2, 4))));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(moments, 0, numOf(0.2), 8, List.of(map)));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.2),
                2, List.of(posterior(2, 0.7, 1, 4))));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.5),
                8, List.of(posterior(8, 0.7, 1, 4))));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(moments, 5, numOf(0.5),
                2, List.of(posterior(2, 0.4, 1, 4), posterior(8, 0.6, 0.5, 5))));
    }

    @Test
    public void recentProbabilityMustCoverAllMassWhenWindowContainsEveryPossibleRunLength() {
        ReturnMoments shortRunMoments = ReturnMoments.stable(7, 3, ReturnRepresentation.LOG, numOf(1), numOf(1),
                numOf(4));
        RunLengthPosterior map = posterior(3, 0.7, 1, 4);

        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(shortRunMoments, 5, numOf(0.7), 3, List.of(map)));

        OnlineChangePointForecastState partialWindow = OnlineChangePointForecastState.stable(
                moments(ReturnRepresentation.LOG), 5, numOf(0.25), 8,
                List.of(posterior(8, 0.7, 1, 4), posterior(2, 0.2, 0.5, 5)));
        assertNumEquals(0.25, partialWindow.recentChangeProbability());
    }

    @Test
    public void subsetMassValidationAllowsCoherentRoundingInTheMomentsFactory() {
        NumFactory lowPrecision = DecimalNumFactory.getInstance(3);
        NumFactory highPrecision = DecimalNumFactory.getInstance(40);
        ReturnMoments lowPrecisionMoments = ReturnMoments.stable(7, 8, ReturnRepresentation.LOG, lowPrecision.one(),
                lowPrecision.one(), lowPrecision.numOf(4));
        List<RunLengthPosterior> roundedSubset = List.of(
                new RunLengthPosterior(8, highPrecision.numOf("0.7"), highPrecision.one(), highPrecision.numOf(4)),
                new RunLengthPosterior(2, highPrecision.numOf("0.1255"), highPrecision.numOf("0.5"),
                        highPrecision.numOf(5)),
                new RunLengthPosterior(3, highPrecision.numOf("0.1245"), highPrecision.numOf("0.4"),
                        highPrecision.numOf(6)));

        OnlineChangePointForecastState state = OnlineChangePointForecastState.stable(lowPrecisionMoments, 5,
                highPrecision.numOf("0.25"), 8, roundedSubset);

        assertNumEquals(0.25, state.recentChangeProbability());
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(lowPrecisionMoments, 5,
                highPrecision.numOf("0.20"), 8, roundedSubset));

        ReturnMoments shortRunMoments = ReturnMoments.stable(7, 3, ReturnRepresentation.LOG, lowPrecision.one(),
                lowPrecision.one(), lowPrecision.numOf(4));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(shortRunMoments, 5, highPrecision.numOf("0.996"), 3,
                        List.of(new RunLengthPosterior(3, highPrecision.numOf("0.7"), highPrecision.one(),
                                highPrecision.numOf(4)))));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineChangePointForecastState.stable(lowPrecisionMoments, 5, lowPrecision.zero(), 2,
                        List.of(new RunLengthPosterior(2, lowPrecision.numOf("0.001"), lowPrecision.one(),
                                lowPrecision.numOf(4)))));
    }

    @Test
    public void lifecycleRepresentationAndMapMomentContractsRemainCoherent() {
        ReturnMoments unstable = ReturnMoments.unstable(7, 6, ReturnRepresentation.LOG);
        ReturnMoments zeroDrift = ReturnMoments.stable(7, 8, ReturnRepresentation.LOG, numOf(1), numOf(0), numOf(4));

        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastState(unstable, 5, numOf(0.1), -1, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastState(unstable, 5, NaN.NaN, 0, List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new OnlineChangePointForecastState(moments(ReturnRepresentation.DECIMAL), 5, numOf(0.1), 8,
                        List.of(posterior(8, 1, 1, 4))));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(unstable, 5,
                numOf(0.1), 8, List.of(posterior(8, 1, 1, 4))));
        assertThrows(IllegalArgumentException.class, () -> OnlineChangePointForecastState.stable(zeroDrift, 5,
                numOf(0.1), 8, List.of(posterior(8, 1, 1, 4))));
    }

    private ReturnMoments moments(ReturnRepresentation representation) {
        return ReturnMoments.stable(7, 8, representation, numOf(1), numOf(1), numOf(4));
    }

    private RunLengthPosterior posterior(int runLength, double probability, double mean, double variance) {
        return new RunLengthPosterior(runLength, numOf(probability), numOf(mean), numOf(variance));
    }
}
