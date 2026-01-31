/*
 * SPDX-License-Identifier: MIT
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
