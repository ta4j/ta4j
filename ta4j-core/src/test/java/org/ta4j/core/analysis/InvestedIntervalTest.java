/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
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

    @Test
    public void handlesEmptySeriesWithoutIntervals() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData().build();
        var tradingRecord = new BaseTradingRecord();

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(series.getEndIndex()).isEqualTo(-1);
        assertThat(series.getBarCount()).isEqualTo(0);
        assertThat(indicator.getValue(0)).isFalse();
    }

    @Test
    public void respectsNonZeroBeginIndexWhenMarkingIntervals() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(1, 1, 1, 1, 1)
                .withMaxBarCount(2)
                .build();
        var tradingRecord = new BaseTradingRecord();
        var price = series.numFactory().one();
        var amount = series.numFactory().one();

        tradingRecord.enter(0, price, amount);

        var indicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        int beginIndex = series.getBeginIndex();
        assertThat(beginIndex).isGreaterThan(0);
        assertThat(indicator.getValue(beginIndex)).as("begin index interval").isFalse();
        assertThat(indicator.getValue(beginIndex + 1)).as("first invested interval after begin index").isTrue();

        var ignoreIndicator = new InvestedInterval(series, tradingRecord, OpenPositionHandling.IGNORE);
        assertThat(ignoreIndicator.getValue(beginIndex + 1)).as("ignored open position interval").isFalse();
    }

    @Test
    public void marksIntervalsCompactlyForOffsetSeries() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1, 1).build();
        BarSeries offset = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(10)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(10, offset), Trade.sellAt(12, offset));
        var indicator = new InvestedInterval(offset, tradingRecord);

        assertThat(offset.getBeginIndex()).isEqualTo(10);
        assertThat(indicator.getValue(10)).as("entry interval").isFalse();
        assertThat(indicator.getValue(11)).as("between entry and exit").isTrue();
        assertThat(indicator.getValue(12)).as("exit interval").isTrue();
        assertThat(indicator.getValue(9)).as("below window").isFalse();
    }

    @Test
    public void marksIntervalEndingAtTerminalBarWithoutOverflow() {
        BarSeries source = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 1).build();
        BarSeries terminal = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withBars(source.getBarData())
                .withBeginIndex(Integer.MAX_VALUE - 1)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(Integer.MAX_VALUE - 1, terminal),
                Trade.sellAt(Integer.MAX_VALUE, terminal));

        var indicator = new InvestedInterval(terminal, tradingRecord);

        assertThat(indicator.getValue(Integer.MAX_VALUE - 1)).as("entry interval").isFalse();
        assertThat(indicator.getValue(Integer.MAX_VALUE)).as("exit interval").isTrue();
    }

    @Test
    public void marksTrailingExitIntervalBeyondLogicalWindowEnd() {
        BarSeries series = ConstrainedSeriesSupport.trailingConstrainedSeries("trailing-exit", numFactory, 1, 10d, 20d,
                30d);
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(1, series), Trade.sellAt(2, series));

        var indicator = new InvestedInterval(series, tradingRecord);

        assertThat(indicator.getValue(2)).as("trailing exit interval").isTrue();
    }

    @Test
    public void intervalsStayAnchoredAfterWindowAdvances() {
        // investedIntervals is materialized against the construction-time
        // window; a later rolling advance of the borrowed series must not
        // rebase the lookup or inherit flags onto never-calculated bars.
        BarSeries rolling = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        rolling.setMaximumBarCount(2);
        rolling.barBuilder().closePrice(30d).add();
        Trade entry = Trade.buyAt(0, rolling);
        rolling.barBuilder().closePrice(40d).add();
        var tradingRecord = new BaseTradingRecord(entry, Trade.sellAt(1, rolling));
        InvestedInterval indicator = new InvestedInterval(rolling, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);

        assertThat(indicator.getValue(1)).isTrue();

        rolling.barBuilder().closePrice(50d).add();

        assertThat(indicator.getValue(1)).as("anchored invested interval").isTrue();
        assertThat(indicator.getValue(2)).as("never-calculated bar stays uninvested").isFalse();
    }
}
