/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.drawdown;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConstrainedSeriesSupport;
import org.ta4j.core.Trade;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.AbstractCriterionTest;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class MaximumDrawdownBarLengthCriterionTest extends AbstractCriterionTest {

    public MaximumDrawdownBarLengthCriterionTest(NumFactory numFactory) {
        super(params -> new MaximumDrawdownBarLengthCriterion(), numFactory);
    }

    @Test
    public void calculateWithNoTrades() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 100, 80, 150).build();
        var maximumDrawdownLength = getCriterion();
        assertNumEquals(0, maximumDrawdownLength.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithOnlyGains() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 140, 160).build();
        var maximumDrawdownLength = getCriterion();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(3, series));
        assertNumEquals(0, maximumDrawdownLength.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithGainsAndLosses() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 100, 80, 150).build();
        var maximumDrawdownLength = getCriterion();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(4, series));
        assertNumEquals(2, maximumDrawdownLength.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithNullSeriesSizeShouldReturn0() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData().build();
        var maximumDrawdownLength = getCriterion();
        assertNumEquals(0, maximumDrawdownLength.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithRealizedModeIgnoresOpenPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 110, 90).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        var markToMarket = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.MARK_TO_MARKET);
        var realized = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.REALIZED);

        assertNumEquals(1, markToMarket.calculate(series, tradingRecord));
        assertNumEquals(0, realized.calculate(series, tradingRecord));
    }

    @Test
    public void lossRealizedAtTheConstrainedBeginFallsOneBarFromTheWindowStart() {
        var series = ConstrainedSeriesSupport.offsetSeries("mdd-length-seeded-begin", numFactory, 1, 3, 0, 100d, 100d,
                100d, 110d);
        var record = new BaseTradingRecord();
        record.enter(0, numFactory.hundred(), numFactory.one());
        record.exit(1, numFactory.numOf(95), numFactory.one());

        // The equity entered the window at the neutral 1 and stood at 0.95 in the first
        // slot.
        assertNumEquals(1, getCriterion().calculate(series, record));
    }

    @Test
    public void calculateWithOpenPositionHandlingChangesDrawdownLength() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        var markToMarket = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.MARK_TO_MARKET);
        var ignoreOpen = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.MARK_TO_MARKET,
                OpenPositionHandling.IGNORE);

        var markToMarketValue = markToMarket.calculate(series, tradingRecord);
        var ignoreValue = ignoreOpen.calculate(series, tradingRecord);

        assertTrue(markToMarketValue.isGreaterThan(ignoreValue));
        assertNumEquals(0, ignoreValue);
    }

    @Test
    public void calculateWithRealizedModeIgnoresOpenHandlingChoice() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        var realizedMarkToMarket = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.MARK_TO_MARKET);
        var realizedIgnore = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.REALIZED,
                OpenPositionHandling.IGNORE);

        assertNumEquals(0, realizedMarkToMarket.calculate(series, tradingRecord));
        assertNumEquals(0, realizedIgnore.calculate(series, tradingRecord));
    }

    @Test
    public void constructorWithOpenPositionHandlingMatchesTwoArgVariant() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 120, 80).build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(1, series));

        for (var handling : OpenPositionHandling.values()) {
            var singleArg = new MaximumDrawdownBarLengthCriterion(handling);
            var explicit = new MaximumDrawdownBarLengthCriterion(EquityCurveMode.MARK_TO_MARKET, handling);

            assertNumEquals(explicit.calculate(series, tradingRecord), singleArg.calculate(series, tradingRecord));
        }
    }

    @Test
    public void matchesRecordCalculationAcrossWindowShapesAndPositionBoundaries() {
        for (var fixture : ConstrainedSeriesSupport.criterionWindowFixtures(numFactory)) {
            if (fixture.position() == null || fixture.position().isOpened()) {
                continue;
            }
            for (var mode : EquityCurveMode.values()) {
                for (var handling : OpenPositionHandling.values()) {
                    var criterion = new MaximumDrawdownBarLengthCriterion(mode, handling);
                    double expected = criterion.calculate(fixture.series(), fixture.tradingRecord()).doubleValue();
                    double actual = criterion.calculate(fixture.series(), fixture.position()).doubleValue();
                    assertEquals(expected, actual, 1e-10, fixture.name() + " " + mode + "/" + handling);
                }
            }
        }
    }

    @Test
    public void betterThan() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numOf(1), numOf(2)));
        assertFalse(criterion.betterThan(numOf(3), numOf(2)));
    }
}
