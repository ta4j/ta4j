/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.serialization.IndicatorSerialization;

public class KalmanNoiseIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public KalmanNoiseIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void scalesPositiveFiniteValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedIndicator<Num> source = new FixedIndicator<>(series, numOf(1), numOf(2), numOf(4));
        KalmanNoiseIndicator noise = new KalmanNoiseIndicator(source, 0.25);

        assertNumEquals(0.25, noise.getValue(0));
        assertNumEquals(0.5, noise.getValue(1));
        assertNumEquals(1, noise.getValue(2));
    }

    @Test
    public void invalidDynamicValuesRemainUnavailable() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        FixedIndicator<Num> source = new FixedIndicator<>(series, numOf(-1), numFactory.zero(), NaN.NaN, numOf(2));
        KalmanNoiseIndicator noise = new KalmanNoiseIndicator(source);

        assertTrue(noise.getValue(0).isNaN());
        assertTrue(noise.getValue(1).isNaN());
        assertTrue(noise.getValue(2).isNaN());
        assertNumEquals(2, noise.getValue(3));
    }

    @Test
    public void rejectsInvalidStaticConfiguration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1).build();
        FixedIndicator<Num> source = new FixedIndicator<>(series, numOf(1));
        Class<? extends RuntimeException> nonFiniteException = numFactory == DecimalNumFactory.getInstance()
                ? NumberFormatException.class
                : IllegalArgumentException.class;

        assertThrows(IllegalArgumentException.class, () -> new KalmanNoiseIndicator(source, 0));
        assertThrows(IllegalArgumentException.class, () -> new KalmanNoiseIndicator(source, -1));
        assertThrows(nonFiniteException, () -> new KalmanNoiseIndicator(source, Double.NaN));
        assertThrows(nonFiniteException, () -> new KalmanNoiseIndicator(source, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> KalmanNoiseIndicator.constant(series, 0));
    }

    @Test
    public void delegatesUnstableBars() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        MockIndicator source = new MockIndicator(series, 2, numOf(1), numOf(2), numOf(3));

        assertEquals(2, new KalmanNoiseIndicator(source).getCountOfUnstableBars());
    }

    @Test
    public void descriptorAndJsonRoundTripsPreserveValues() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        FixedIndicator<Num> source = new FixedIndicator<>(series, numOf(1), numOf(2), numOf(4));
        KalmanNoiseIndicator original = new KalmanNoiseIndicator(source, 0.25);

        Indicator<?> descriptorCopy = IndicatorSerialization.fromDescriptor(series, original.toDescriptor());
        Indicator<?> jsonCopy = Indicator.fromJson(series, original.toJson());

        assertEquals(original.toDescriptor(), descriptorCopy.toDescriptor());
        assertEquals(original.toDescriptor(), jsonCopy.toDescriptor());
        for (int i = 0; i <= series.getEndIndex(); i++) {
            assertEquals(original.getValue(i), descriptorCopy.getValue(i));
            assertEquals(original.getValue(i), jsonCopy.getValue(i));
        }
    }
}
