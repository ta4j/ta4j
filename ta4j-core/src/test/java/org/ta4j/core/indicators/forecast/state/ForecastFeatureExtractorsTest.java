/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastFeatureExtractorsTest extends AbstractIndicatorTest<ForecastFeatureExtractor<ForecastState>, Num> {

    public ForecastFeatureExtractorsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void commonExtractorsPreserveDocumentedFeatureOrder() {
        ReturnForecastState state = state();

        assertArrayEquals(new double[] { 1, 2, 3, 4 },
                ForecastFeatureExtractors.<ForecastState>meanDriftVarianceVolatility().features(state), 0d);
        assertArrayEquals(new double[] { 2, 4 },
                ForecastFeatureExtractors.<ForecastState>driftVolatility().features(state), 0d);
        assertArrayEquals(new double[] { 1, 4 }, ForecastFeatureExtractors.returnStateDefaults().features(state), 0d);
    }

    @Test
    public void returnStateDefaultsComposeWithGenericForecastStates() {
        ForecastFeatureExtractor<ForecastState> extractor = ForecastFeatureExtractors.returnStateDefaults();

        assertArrayEquals(new double[] { 1, 4 }, extractor.features(state()), 0d);
    }

    @Test
    public void extractorsReturnDefensiveArrays() {
        ForecastFeatureExtractor<ReturnForecastState> extractor = ForecastFeatureExtractors.returnStateDefaults();

        double[] first = extractor.features(state());
        double[] second = extractor.features(state());

        assertNotSame(first, second);
        first[0] = 99;
        assertArrayEquals(new double[] { 1, 4 }, second, 0d);
    }

    @Test
    public void extractorsRejectNullAndUnstableStates() {
        ForecastFeatureExtractor<ForecastState> extractor = ForecastFeatureExtractors.meanDriftVarianceVolatility();

        assertThrows(NullPointerException.class, () -> extractor.features(null));
        assertThrows(IllegalArgumentException.class, () -> extractor.features(ReturnForecastState.unstable(0)));
    }

    @Test
    public void extractorsRejectFiniteNumsThatOverflowPrimitiveBoundary() {
        Num largeFiniteValue = DecimalNumFactory.getInstance().numOf("1E+10000");
        ReturnForecastState state = new ReturnForecastState(3, 4, true, largeFiniteValue, numOf(2), numOf(3), numOf(4));

        assertThrows(IllegalArgumentException.class,
                () -> ForecastFeatureExtractors.returnStateDefaults().features(state));
    }

    private ReturnForecastState state() {
        return new ReturnForecastState(3, 4, true, numFactory.one(), numFactory.two(), numFactory.three(), numOf(4));
    }
}
