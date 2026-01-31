/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class MaximumDrawdownCriterionTest extends AbstractCriterionTest {

    public MaximumDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> new MaximumDrawdownCriterion(), numFactory);
    }

    @Test
    public void calculateWithNoTrades() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 5, 20, 3).build();
        var mdd = getCriterion();

        assertNumEquals(0d, mdd.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithOnlyGains() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 8, 20, 3).build();
        var mdd = getCriterion();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(5, series));

        assertNumEquals(0d, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithGainsAndLosses() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3, 6, 5, 20, 3).build();
        var mdd = getCriterion();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(3, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(6, series));

        assertNumEquals(.875d, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithRealizedEquityCurveIgnoresInterimDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 80, 120).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var markToMarket = new MaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET);
        var realized = new MaximumDrawdownCriterion(EquityCurveMode.REALIZED);

        assertNumEquals(0.2d, markToMarket.calculate(series, tradingRecord));
        assertNumEquals(0d, realized.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNullSeriesSizeShouldReturn0() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(new double[] {}).build();
        var mdd = getCriterion();
        assertNumEquals(0d, mdd.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void withTradesThatSellBeforeBuying() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(2, 1, 3, 5, 6, 3, 20).build();
        var mdd = getCriterion();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(3, series), Trade.sellAt(4, series), Trade.sellAt(5, series), Trade.buyAt(6, series));
        assertNumEquals(3.8d, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void withSimpleTrades() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 10, 5, 6, 1).build();
        var mdd = getCriterion();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series), Trade.sellAt(2, series), Trade.buyAt(2, series), Trade.sellAt(3, series),
                Trade.buyAt(3, series), Trade.sellAt(4, series));
        assertNumEquals(.9d, mdd.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithRealizedModeIgnoresOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 90).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        var markToMarket = new MaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET);
        var realized = new MaximumDrawdownCriterion(EquityCurveMode.REALIZED);

        assertNumEquals(0.181818d, markToMarket.calculate(series, tradingRecord));
        assertNumEquals(0d, realized.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithOpenPositionHandlingChangesDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        var markToMarket = new MaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        var ignoreOpen = new MaximumDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET, OpenPositionHandling.IGNORE);

        var markToMarketValue = markToMarket.calculate(series, tradingRecord);
        var ignoreValue = ignoreOpen.calculate(series, tradingRecord);

        assertTrue(markToMarketValue.isGreaterThan(ignoreValue));
        assertNumEquals(0d, ignoreValue);
    }

    @Test
    public void calculateWithRealizedModeIgnoresOpenHandlingChoice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        var realizedMarkToMarket = new MaximumDrawdownCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        var realizedIgnore = new MaximumDrawdownCriterion(EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        assertNumEquals(0d, realizedMarkToMarket.calculate(series, tradingRecord));
        assertNumEquals(0d, realizedIgnore.calculate(series, tradingRecord));
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(0.9), numOf(1.5)));
        assertFalse(criterion.betterThan(numOf(1.2), numOf(0.4)));
    }
}
