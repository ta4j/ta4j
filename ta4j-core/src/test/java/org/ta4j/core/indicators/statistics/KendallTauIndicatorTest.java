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
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class KendallTauIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    public KendallTauIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void calculatesPerfectAssociation() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        Indicator<Num> first = indicator(series, 1, 2, 3, 4);
        Indicator<Num> second = indicator(series, 2, 4, 6, 8);
        KendallTauIndicator correlation = new KendallTauIndicator(first, second, 4);

        assertTrue(correlation.getValue(2).isNaN());
        assertNumEquals(1, correlation.getValue(3));
        assertEquals(3, correlation.getCountOfUnstableBars());
    }

    @Test
    public void appliesTauBTieCorrection() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4).build();
        Indicator<Num> first = indicator(series, 1, 2, 2, 3);
        Indicator<Num> second = indicator(series, 1, 2, 3, 4);
        KendallTauIndicator correlation = new KendallTauIndicator(first, second, 4);

        assertNumEquals(0.9128709291752769, correlation.getValue(3));
    }

    @Test
    public void returnsNaNWhenDenominatorIsUndefined() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1).build();
        Indicator<Num> constant = indicator(series, 1, 1, 1, 1);
        Indicator<Num> changing = indicator(series, 1, 2, 3, 4);
        KendallTauIndicator correlation = new KendallTauIndicator(constant, changing, 4);

        assertTrue(correlation.getValue(3).isNaN());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void serializesAndRestoresFromJson() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 4, 5).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator average = new SMAIndicator(close, 2);
        KendallTauIndicator correlation = new KendallTauIndicator(close, average, 4);

        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, correlation.toJson());

        assertTrue(restored instanceof KendallTauIndicator);
        assertNumEquals(correlation.getValue(4), restored.getValue(4), 1.0e-12);
    }

    @Test
    public void rejectsInvalidBarCount() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2).build();
        Indicator<Num> indicator = indicator(series, 1, 2);

        assertThrows(IllegalArgumentException.class, () -> new KendallTauIndicator(indicator, indicator, 0));
    }

    private Indicator<Num> indicator(BarSeries series, Number... values) {
        List<Num> nums = java.util.Arrays.stream(values).map(numFactory::numOf).toList();
        return new MockIndicator(series, nums);
    }
}
