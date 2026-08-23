/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.helpers;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

public class AverageIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private Indicator<Num> one;
    private Indicator<Num> two;
    private Indicator<Num> three;
    private Indicator<Num> nan;
    private Indicator<Num> nanPartly;
    private Indicator<Num> sma;

    public AverageIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        BarSeries barSeries = new MockBarSeriesBuilder().withNumFactory(numFactory).withDefaultData().build();
        one = new ConstantIndicator<>(barSeries, numFactory.numOf(1.0));
        two = new ConstantIndicator<>(barSeries, numFactory.numOf(2.0));
        three = new ConstantIndicator<>(barSeries, numFactory.numOf(3.0));
        nan = new ConstantIndicator<>(barSeries, NaN);
        nanPartly = new FixedIndicator<>(barSeries, numFactory.numOf(4.0), NaN, numFactory.numOf(5.0));
        sma = new SMAIndicator(one, 4);
    }

    @Test
    public void indicatorShouldRetrieveAverageOfOneAndTwo() {
        AverageIndicator averageIndicator = new AverageIndicator(one, two);

        for (int i = 1; i < 10; i++) {
            assertNumEquals(1.5, averageIndicator.getValue(i));
        }
    }

    @Test
    public void indicatorShouldRetrieveAverageOfOneAndTwoAndThree() {
        AverageIndicator averageIndicator = new AverageIndicator(one, two, three);

        for (int i = 1; i < 10; i++) {
            assertNumEquals(2, averageIndicator.getValue(i));
        }
    }

    @Test
    public void indicatorShouldRetrieveNaN() {
        AverageIndicator averageIndicator = new AverageIndicator(nan);

        for (int i = 1; i < 10; i++) {
            assertNumEquals(NaN, averageIndicator.getValue(i));
        }
    }

    @Test
    public void indicatorShouldRetrieveNaNpartly() {
        AverageIndicator averageIndicator = new AverageIndicator(nanPartly, one);

        assertNumEquals(2.5, averageIndicator.getValue(0));
        assertNumEquals(NaN, averageIndicator.getValue(1));
        assertNumEquals(3, averageIndicator.getValue(2));
    }

    @Test
    public void indicatorGetCountOfUnstableBarsReturnsMaxAmongIndicators() {
        AverageIndicator averageIndicator = new AverageIndicator(sma, one);

        assertEquals(sma.getCountOfUnstableBars(), averageIndicator.getCountOfUnstableBars());
    }

    @Test
    public void indicatorShouldFailOnNull() {
        assertThrows(IllegalArgumentException.class, AverageIndicator::new);
    }

    @Test
    public void indicatorShouldFailOnEmptyList() {
        assertThrows(IllegalArgumentException.class, () -> new AverageIndicator(Collections.emptyList()));
    }

    @Test
    public void indicatorShouldFailOnListContainsNull() {
        assertThrows(IllegalArgumentException.class, () -> new AverageIndicator(Arrays.asList(one, null)));
    }

    @Test
    public void indicatorShouldFailOnNullVarargsArray() {
        assertThrows(IllegalArgumentException.class, () -> new AverageIndicator((Indicator<Num>[]) null));
    }

    @Test
    public void indicatorShouldFailOnDifferentSeries() {
        BarSeries other = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        assertThrows(IllegalArgumentException.class, () -> new AverageIndicator(one, new ClosePriceIndicator(other)));
    }
}
