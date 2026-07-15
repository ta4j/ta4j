/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class ForecastFeatureExtractorsTest
        extends AbstractIndicatorTest<ForecastFeatureExtractor<ReturnForecastState>, Num> {

    public ForecastFeatureExtractorsTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void schemasOssifyIdentityOrderUnitsAndDimensions() {
        ForecastFeatureExtractor<ReturnForecastState> meanVolatility = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);
        ForecastFeatureExtractor<ReturnForecastState> driftVolatility = ForecastFeatureExtractors
                .driftVolatility(ReturnRepresentation.LOG);
        ForecastFeatureExtractor<ReturnForecastState> full = ForecastFeatureExtractors
                .meanDriftVariance(ReturnRepresentation.LOG);

        assertEquals("return-moments/mean-volatility", meanVolatility.schema().id());
        assertEquals(1, meanVolatility.schema().version());
        assertEquals(2, meanVolatility.schema().dimension());
        assertEquals("mean", meanVolatility.schema().features().get(0).name());
        assertEquals("log-return", meanVolatility.schema().features().get(0).unit());
        assertArrayEquals(new double[] { 1, 4 }, meanVolatility.features(state()), 0d);
        assertArrayEquals(new double[] { 2, 4 }, driftVolatility.features(state()), 0d);
        assertArrayEquals(new double[] { 1, 2, 16 }, full.features(state()), 0d);
        assertThrows(UnsupportedOperationException.class,
                () -> meanVolatility.schema().features().add(new ForecastFeatureSchema.Feature("x", "x")));
    }

    @Test
    public void roughVolatilitySchemaOssifiesSpecializedShapeAndPrimitiveBoundaries() {
        ForecastFeatureExtractor<RoughVolatilityForecastState> extractor = ForecastFeatureExtractors.roughVolatility();
        RoughVolatilityForecastState state = RoughVolatilityForecastState.stable(
                ReturnMoments.stable(3, 4, ReturnRepresentation.LOG, numOf(1), numOf(2), numOf(16)), numOf(0.1),
                numOf(0.25), List.of(numOf(16), numOf(20)));

        assertEquals("rough-volatility/default", extractor.schema().id());
        assertEquals(1, extractor.schema().version());
        assertEquals(ReturnRepresentation.LOG, extractor.schema().representation());
        assertEquals(4, extractor.schema().dimension());
        assertEquals("mean", extractor.schema().features().get(0).name());
        assertEquals("volatility", extractor.schema().features().get(1).name());
        assertEquals("roughness_hurst", extractor.schema().features().get(2).name());
        assertEquals("vol_of_vol", extractor.schema().features().get(3).name());
        assertArrayEquals(new double[] { 1, 4, 0.1, 0.25 }, extractor.features(state), 1e-12);
        assertThrows(IllegalArgumentException.class,
                () -> extractor.features(RoughVolatilityForecastState.unstable(3, 4)));

        NumFactory highPrecision = DecimalNumFactory.getInstance(40);
        RoughVolatilityForecastState overflow = RoughVolatilityForecastState.stable(
                ReturnMoments.stable(3, 4, ReturnRepresentation.LOG, highPrecision.one(), highPrecision.two(),
                        highPrecision.numOf(16)),
                highPrecision.numOf(0.1), highPrecision.numOf("1E+10000"), List.of(highPrecision.numOf(16)));
        assertThrows(IllegalArgumentException.class, () -> extractor.features(overflow));
    }

    @Test
    public void changePointSchemaOssifiesSpecializedShapeUnitsAndPrimitiveBoundaries() {
        ForecastFeatureExtractor<OnlineChangePointForecastState> extractor = ForecastFeatureExtractors.changePoint();
        OnlineChangePointForecastState state = OnlineChangePointForecastState.stable(
                ReturnMoments.stable(7, 8, ReturnRepresentation.LOG, numOf(1), numOf(1), numOf(16)), numOf(0.25), 8,
                List.of(new RunLengthPosterior(8, numOf(0.7), numOf(1), numOf(16)),
                        new RunLengthPosterior(2, numOf(0.2), numOf(0.5), numOf(20))));

        assertEquals("change-point/default", extractor.schema().id());
        assertEquals(1, extractor.schema().version());
        assertEquals(ReturnRepresentation.LOG, extractor.schema().representation());
        assertEquals(4, extractor.schema().dimension());
        assertEquals("mean", extractor.schema().features().get(0).name());
        assertEquals("log-return", extractor.schema().features().get(0).unit());
        assertEquals("volatility", extractor.schema().features().get(1).name());
        assertEquals("log-return", extractor.schema().features().get(1).unit());
        assertEquals("recent_change_probability", extractor.schema().features().get(2).name());
        assertEquals("probability", extractor.schema().features().get(2).unit());
        assertEquals("most_likely_run_length", extractor.schema().features().get(3).name());
        assertEquals("observations", extractor.schema().features().get(3).unit());
        assertArrayEquals(new double[] { 1, 4, 0.25, 8 }, extractor.features(state), 0d);
        assertThrows(IllegalArgumentException.class,
                () -> extractor.features(OnlineChangePointForecastState.unstable(3, 4)));

        NumFactory highPrecision = DecimalNumFactory.getInstance(40);
        OnlineChangePointForecastState underflow = OnlineChangePointForecastState.stable(
                ReturnMoments.stable(7, 8, ReturnRepresentation.LOG, highPrecision.one(), highPrecision.one(),
                        highPrecision.numOf(16)),
                highPrecision.numOf("1E-10000"), 8,
                List.of(new RunLengthPosterior(8, highPrecision.one(), highPrecision.one(), highPrecision.numOf(16))));
        assertThrows(IllegalArgumentException.class, () -> extractor.features(underflow));
    }

    @Test
    public void extractionSupportsCallerOwnedArraysAndDefensiveConvenienceArrays() {
        ForecastFeatureExtractor<ReturnForecastState> extractor = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);
        double[] target = { -1, -1, -1, -1 };

        extractor.extractInto(state(), target, 1);
        double[] first = extractor.features(state());
        double[] second = extractor.features(state());

        assertArrayEquals(new double[] { -1, 1, 4, -1 }, target, 0d);
        assertNotSame(first, second);
        first[0] = 99;
        assertArrayEquals(new double[] { 1, 4 }, second, 0d);
        assertThrows(IndexOutOfBoundsException.class, () -> extractor.extractInto(state(), target, 3));
    }

    @Test
    public void extractionRejectsUnstableAndRepresentationMismatch() {
        ForecastFeatureExtractor<ReturnForecastState> extractor = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);
        ReturnForecastState decimalState = ReturnForecastState.stable(3, 4, ReturnRepresentation.DECIMAL, numOf(1),
                numOf(2), numOf(16));

        assertThrows(NullPointerException.class, () -> extractor.features(null));
        assertThrows(IllegalArgumentException.class,
                () -> extractor.features(ReturnForecastState.unstable(3, 4, ReturnRepresentation.LOG)));
        assertThrows(IllegalArgumentException.class, () -> extractor.features(decimalState));
    }

    @Test
    public void extractionRejectsPrimitiveOverflowAndNonzeroUnderflow() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance(40);
        ReturnForecastState overflow = ReturnForecastState.stable(3, 4, ReturnRepresentation.LOG,
                decimalFactory.numOf("1E+10000"), decimalFactory.zero(), decimalFactory.one());
        ReturnForecastState underflow = ReturnForecastState.stable(3, 4, ReturnRepresentation.LOG,
                decimalFactory.numOf("1E-10000"), decimalFactory.zero(), decimalFactory.one());
        ForecastFeatureExtractor<ReturnForecastState> extractor = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);

        assertThrows(IllegalArgumentException.class, () -> extractor.features(overflow));
        assertThrows(IllegalArgumentException.class, () -> extractor.features(underflow));
    }

    @Test
    public void extractionUsesCanonicalMomentsInsteadOfOverridableDelegates() {
        ReturnMoments moments = ReturnMoments.stable(3, 4, ReturnRepresentation.LOG, numFactory.one(), numFactory.two(),
                numOf(16));
        ForecastFeatureExtractor<MisleadingMomentState> extractor = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);

        assertArrayEquals(new double[] { 1, 4 }, extractor.features(new MisleadingMomentState(moments)), 0d);
    }

    @Test
    public void extractionRejectsMissingCanonicalMoments() {
        ForecastFeatureExtractor<NullMomentState> extractor = ForecastFeatureExtractors
                .meanVolatility(ReturnRepresentation.LOG);

        assertThrows(IllegalArgumentException.class, () -> extractor.features(new NullMomentState()));
    }

    private ReturnForecastState state() {
        return ReturnForecastState.stable(3, 4, ReturnRepresentation.LOG, numFactory.one(), numFactory.two(),
                numOf(16));
    }

    private record MisleadingMomentState(ReturnMoments moments) implements ReturnMomentState {

        @Override
        public boolean isStable() {
            return false;
        }

        @Override
        public ReturnRepresentation representation() {
            return ReturnRepresentation.DECIMAL;
        }

        @Override
        public Num mean() {
            return moments.mean().plus(moments.mean().getNumFactory().one());
        }
    }

    private static final class NullMomentState implements ReturnMomentState {

        @Override
        public ReturnMoments moments() {
            return null;
        }
    }
}
