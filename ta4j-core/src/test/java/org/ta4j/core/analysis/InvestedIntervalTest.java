/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
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
package org.ta4j.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.junit.Test;

public class InvestedIntervalTest extends AbstractIndicatorTest<Indicator<Boolean>, Num> {

    public InvestedIntervalTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void marksIntervalsForClosedAndOpenPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
        var tradingRecord = new BaseTradingRecord();
        var price = series.numFactory().numOf(1);
        var amount = series.numFactory().numOf(1);

        tradingRecord.enter(1, price, amount);
        tradingRecord.exit(3, price, amount);
        tradingRecord.enter(4, price, amount);

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(indicator.getValue(0)).as("first bar interval").isFalse();
        assertThat(indicator.getValue(1)).as("entry bar interval").isFalse();
        assertThat(indicator.getValue(2)).as("between entry and exit").isTrue();
        assertThat(indicator.getValue(3)).as("exit interval").isTrue();
        assertThat(indicator.getValue(4)).as("open position entry interval").isFalse();
        assertThat(indicator.getValue(5)).as("open position following interval").isTrue();
    }

    @Test
    public void returnsFalseWhenNoPositionsExist() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1).build();
        var tradingRecord = new BaseTradingRecord();

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(indicator.getValue(0)).isFalse();
        assertThat(indicator.getValue(1)).isFalse();
        assertThat(indicator.getValue(2)).isFalse();
    }

    @Test
    public void ignoresOpenPositionsWhenConfigured() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1, 1, 1, 1).build();
        var tradingRecord = new BaseTradingRecord();
        var price = series.numFactory().numOf(1);
        var amount = series.numFactory().numOf(1);

        tradingRecord.enter(1, price, amount);
        tradingRecord.exit(3, price, amount);
        tradingRecord.enter(4, price, amount);

        var indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.IGNORE);

        assertThat(indicator.getValue(2)).as("between entry and exit").isTrue();
        assertThat(indicator.getValue(3)).as("exit interval").isTrue();
        assertThat(indicator.getValue(4)).as("open position entry interval").isFalse();
        assertThat(indicator.getValue(5)).as("open position following interval").isFalse();
    }

}
