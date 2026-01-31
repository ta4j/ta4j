/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class MaximumAbsoluteDrawdownCriterionTest extends AbstractCriterionTest {

    public MaximumAbsoluteDrawdownCriterionTest(NumFactory numFactory) {
        super(params -> new MaximumAbsoluteDrawdownCriterion(), numFactory);
    }

    @Test
    public void calculateWithNoTrades() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(1, 2, 3).build();
        var criterion = getCriterion();
        assertNumEquals(0, criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithGainsAndLosses() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 110, 105, 120, 100, 50)
                .build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(2, series),
                Trade.sellAt(3, series), Trade.buyAt(4, series), Trade.sellAt(5, series));

        var criterion = getCriterion();
        assertNumEquals(50.0, criterion.calculate(series, record));
    }

    @Test
    public void calculateWithOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var criterion = getCriterion();
        assertNumEquals(40.0, criterion.calculate(series, record));
    }

    @Test
    public void calculateWithPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 90).build();
        var position = new Position(Trade.buyAt(0, series), Trade.sellAt(2, series));

        var criterion = getCriterion();
        assertNumEquals(20.0, criterion.calculate(series, position));
    }

    @Test
    public void calculateWithRealizedModeIgnoresOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series));

        var criterion = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.REALIZED);
        assertNumEquals(0d, criterion.calculate(series, record));
    }

    @Test
    public void calculateWithOpenPositionHandlingChangesAbsoluteDrawdown() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(1, series));

        var markToMarket = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        var ignoreOpen = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        var markToMarketValue = markToMarket.calculate(series, record);
        var ignoreValue = ignoreOpen.calculate(series, record);

        assertTrue(markToMarketValue.isGreaterThan(ignoreValue));
        assertNumEquals(0d, ignoreValue);
    }

    @Test
    public void calculateWithRealizedModeIgnoresOpenHandlingChoice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var record = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series), Trade.buyAt(1, series));

        var realizedMarkToMarket = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        var realizedIgnore = new MaximumAbsoluteDrawdownCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        assertNumEquals(0d, realizedMarkToMarket.calculate(series, record));
        assertNumEquals(0d, realizedIgnore.calculate(series, record));
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(1), numOf(2)));
        assertFalse(criterion.betterThan(numOf(2), numOf(1)));
    }

}
