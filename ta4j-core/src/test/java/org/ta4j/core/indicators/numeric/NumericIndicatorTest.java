/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.indicators.numeric;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.HighestValueIndicator;
import org.ta4j.core.indicators.helpers.LowestValueIndicator;
import org.ta4j.core.indicators.helpers.VolumeIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.UnderIndicatorRule;

public class NumericIndicatorTest extends AbstractIndicatorTest<NumericIndicator, Num> {

    private final BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
            .withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0, -1, -2)
            .build();
    private final ClosePriceIndicator cp1 = new ClosePriceIndicator(series);
    private final EMAIndicator ema = new EMAIndicator(cp1, 3);

    public NumericIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void plus() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        final NumericIndicator staticOp = numericIndicator.plus(5);
        assertNumEquals(1 + 5, staticOp.getValue(0));
        assertNumEquals(9 + 5, staticOp.getValue(8));

        final NumericIndicator dynamicOp = numericIndicator.plus(ema);
        assertNumEquals(cp1.getValue(0).plus(ema.getValue(0)), dynamicOp.getValue(0));
        assertNumEquals(cp1.getValue(8).plus(ema.getValue(8)), dynamicOp.getValue(8));
    }

    @Test
    public void minus() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        final NumericIndicator staticOp = numericIndicator.minus(5);
        assertNumEquals(1 - 5, staticOp.getValue(0));
        assertNumEquals(9 - 5, staticOp.getValue(8));

        final NumericIndicator dynamicOp = numericIndicator.minus(ema);
        assertNumEquals(cp1.getValue(0).minus(ema.getValue(0)), dynamicOp.getValue(0));
        assertNumEquals(cp1.getValue(8).minus(ema.getValue(8)), dynamicOp.getValue(8));
    }

    @Test
    public void multipliedBy() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        final NumericIndicator staticOp = numericIndicator.multipliedBy(5);
        assertNumEquals(1 * 5, staticOp.getValue(0));
        assertNumEquals(9 * 5, staticOp.getValue(8));

        final NumericIndicator dynamicOp = numericIndicator.multipliedBy(ema);
        assertNumEquals(cp1.getValue(0).multipliedBy(ema.getValue(0)), dynamicOp.getValue(0));
        assertNumEquals(cp1.getValue(8).multipliedBy(ema.getValue(8)), dynamicOp.getValue(8));
    }

    @Test
    public void dividedBy() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        final NumericIndicator staticOp = numericIndicator.dividedBy(5);
        assertNumEquals(1 / 5.0, staticOp.getValue(0));
        assertNumEquals(9 / 5.0, staticOp.getValue(8));

        final NumericIndicator zeroOp = numericIndicator.dividedBy(0);
        assertNumEquals(NaN.NaN, zeroOp.getValue(0));
        assertNumEquals(NaN.NaN, zeroOp.getValue(8));

        final NumericIndicator dynamicOp = numericIndicator.dividedBy(ema);
        assertNumEquals(cp1.getValue(0).dividedBy(ema.getValue(0)), dynamicOp.getValue(0));
        assertNumEquals(cp1.getValue(8).dividedBy(ema.getValue(8)), dynamicOp.getValue(8));
    }

    @Test
    public void max() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        final NumericIndicator staticOp = numericIndicator.max(5);
        assertNumEquals(5, staticOp.getValue(0));
        assertNumEquals(9, staticOp.getValue(8));

        final NumericIndicator dynamicOp = numericIndicator.max(ema);
        assertNumEquals(cp1.getValue(0).max(ema.getValue(0)), dynamicOp.getValue(0));
        assertNumEquals(cp1.getValue(8).max(ema.getValue(8)), dynamicOp.getValue(8));
    }

    @Test
    public void min() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        final NumericIndicator staticOp = numericIndicator.min(5);
        assertNumEquals(1, staticOp.getValue(0));
        assertNumEquals(5, staticOp.getValue(8));

        final NumericIndicator dynamicOp = numericIndicator.min(ema);
        assertNumEquals(cp1.getValue(0).min(ema.getValue(0)), dynamicOp.getValue(0));
        assertNumEquals(cp1.getValue(8).min(ema.getValue(8)), dynamicOp.getValue(8));
    }

    @Test
    public void sqrt() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);
        final NumericIndicator dynamicOp = numericIndicator.sqrt();
        assertNumEquals(1, dynamicOp.getValue(0));
        assertNumEquals(Math.sqrt(2.0), dynamicOp.getValue(1));
        assertNumEquals(3, dynamicOp.getValue(8));
    }

    @Test
    public void abs() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);
        final NumericIndicator dynamicOp = numericIndicator.abs();
        assertNumEquals(1, dynamicOp.getValue(0));
        assertNumEquals(2, dynamicOp.getValue(series.getBarCount() - 1));
    }

    @Test
    public void squared() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);
        final NumericIndicator dynamicOp = numericIndicator.squared();
        assertNumEquals(1, dynamicOp.getValue(0));
        assertNumEquals(81, dynamicOp.getValue(8));
    }

    @Test
    public void indicators() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        assertEquals(SMAIndicator.class, numericIndicator.sma(5).delegate().getClass());
        assertEquals(EMAIndicator.class, numericIndicator.ema(5).delegate().getClass());
        assertEquals(StandardDeviationIndicator.class, numericIndicator.stddev(5).delegate().getClass());
        assertEquals(HighestValueIndicator.class, numericIndicator.highest(5).delegate().getClass());
        assertEquals(LowestValueIndicator.class, numericIndicator.lowest(5).delegate().getClass());

        assertEquals(ClosePriceIndicator.class, NumericIndicator.closePrice(series).delegate().getClass());
        assertEquals(VolumeIndicator.class, NumericIndicator.volume(series).delegate().getClass());

        ADXIndicator adx = new ADXIndicator(series, 5);
        assertEquals(ADXIndicator.class, NumericIndicator.of(adx).delegate().getClass());
    }

    @Test
    public void rules() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        assertEquals(CrossedUpIndicatorRule.class, numericIndicator.crossedOver(5).getClass());
        assertEquals(CrossedUpIndicatorRule.class, numericIndicator.crossedOver(ema).getClass());
        assertEquals(CrossedDownIndicatorRule.class, numericIndicator.crossedUnder(5).getClass());
        assertEquals(CrossedDownIndicatorRule.class, numericIndicator.crossedUnder(ema).getClass());
        assertEquals(OverIndicatorRule.class, numericIndicator.isGreaterThan(5).getClass());
        assertEquals(OverIndicatorRule.class, numericIndicator.isGreaterThan(ema).getClass());
        assertEquals(UnderIndicatorRule.class, numericIndicator.isLessThan(5).getClass());
        assertEquals(UnderIndicatorRule.class, numericIndicator.isLessThan(ema).getClass());
    }

    @Test
    public void previous() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);
        final Indicator<Num> previous = numericIndicator.previous();
        assertNumEquals(cp1.getValue(0), previous.getValue(1));

        final Indicator<Num> previous3 = numericIndicator.previous(3);
        assertNumEquals(cp1.getValue(3), previous3.getValue(6));
    }

    @Test
    public void getValue() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);

        for (int i = 0; i < series.getBarCount(); i++) {
            assertNumEquals(cp1.getValue(i), numericIndicator.getValue(i));
        }
    }

    @Test
    public void barSeries() {
        final NumericIndicator numericIndicator = NumericIndicator.of(cp1);
        assertEquals(cp1.getBarSeries(), numericIndicator.getBarSeries());
    }
}
