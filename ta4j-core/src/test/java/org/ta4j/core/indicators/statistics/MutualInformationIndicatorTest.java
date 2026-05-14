/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class MutualInformationIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public MutualInformationIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void returnsNaturalLogTwoForPerfectBinaryDependence() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 0, 1, 1).build();
        Indicator<Num> first = indicator(series, 0, 0, 1, 1);
        Indicator<Num> second = indicator(series, 0, 0, 1, 1);
        MutualInformationIndicator mutualInformation = new MutualInformationIndicator(first, second, 4, 2);

        assertTrue(mutualInformation.getValue(2).isNaN());
        assertNumEquals(numOf(Math.log(2.0)), mutualInformation.getValue(3), 1.0e-12);
        assertEquals(3, mutualInformation.getCountOfUnstableBars());
    }

    @Test
    public void returnsZeroForIndependentBinarySamples() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(0, 0, 1, 1).build();
        Indicator<Num> first = indicator(series, 0, 0, 1, 1);
        Indicator<Num> second = indicator(series, 0, 1, 0, 1);
        MutualInformationIndicator mutualInformation = new MutualInformationIndicator(first, second, 4, 2);

        assertNumEquals(0, mutualInformation.getValue(3));
    }

    @Test
    public void returnsZeroWhenOneSeriesIsConstant() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1).build();
        Indicator<Num> constant = indicator(series, 1, 1, 1, 1);
        Indicator<Num> changing = indicator(series, 1, 2, 3, 4);
        MutualInformationIndicator mutualInformation = new MutualInformationIndicator(constant, changing, 4, 2);

        assertNumEquals(0, mutualInformation.getValue(3));
    }

    @Test
    public void rejectsInvalidConfiguration() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> indicator = indicator(series, 1, 2);

        assertThrows(IllegalArgumentException.class, () -> new MutualInformationIndicator(indicator, indicator, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new MutualInformationIndicator(indicator, indicator, 2, 1));
    }

    private Indicator<Num> indicator(BarSeries series, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, nums);
    }
}
