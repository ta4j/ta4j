/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.MedianPriceIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class AwesomeOscillatorIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {
    private BarSeries series;

    public AwesomeOscillatorIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(16).lowPrice(8).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(12).lowPrice(6).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(18).lowPrice(14).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(10).lowPrice(6).add();
        series.barBuilder().openPrice(0).closePrice(0).highPrice(8).lowPrice(4).add();
    }

    @Test
    public void calculateWithSma2AndSma3() {
        var awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), 2, 3);

        assertNumEquals(0, awesome.getValue(0));
        assertNumEquals(0, awesome.getValue(1));
        assertNumEquals(1d / 6, awesome.getValue(2));
        assertNumEquals(1, awesome.getValue(3));
        assertNumEquals(-3, awesome.getValue(4));
    }

    @Test
    public void withSma1AndSma2() {
        var awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series), 1, 2);

        assertNumEquals(0, awesome.getValue(0));
        assertNumEquals("-1.5", awesome.getValue(1));
        assertNumEquals("3.5", awesome.getValue(2));
        assertNumEquals(-4, awesome.getValue(3));
        assertNumEquals(-1, awesome.getValue(4));
    }

    @Test
    public void withSmaDefault() {
        var awesome = new AwesomeOscillatorIndicator(new MedianPriceIndicator(series));

        assertNumEquals(0, awesome.getValue(0));
        assertNumEquals(0, awesome.getValue(1));
        assertNumEquals(0, awesome.getValue(2));
        assertNumEquals(0, awesome.getValue(3));
        assertNumEquals(0, awesome.getValue(4));
    }

}
