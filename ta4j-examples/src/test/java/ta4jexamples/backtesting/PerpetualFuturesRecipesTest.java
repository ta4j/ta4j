/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

class PerpetualFuturesRecipesTest {
    @Test
    void minimumCommissionAppliesPerTradeButNotToZeroExposure() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        CostModel fees = new PerpetualFuturesRecipes.MinimumCommission(0.001, 0.25);
        assertEquals(0, fees.calculate(series.numFactory().numOf(100), series.numFactory().zero()).doubleValue(), 1e-9);
        assertEquals(0.25, fees.calculate(series.numFactory().numOf(100), series.numFactory().one()).doubleValue(),
                1e-9);
        assertEquals(1, fees.calculate(series.numFactory().numOf(100), series.numFactory().numOf(10)).doubleValue(),
                1e-9);
        Indicator<Num> rates = PerpetualFuturesRecipes.historicalRates(series);
        TradingRecord record = PerpetualFuturesRecipes.run(series,
                PerpetualFuturesRecipes.fundingAwareStrategy(series, rates), fees, new ZeroCostModel(), 1, 1,
                new BigDecimal("0.001"));
        Position position = record.getPositions().getFirst();
        assertEquals(0.5, position.getEntry().getCost().plus(position.getExit().getCost()).doubleValue(), 1e-9);
        assertEquals(39.5, position.getProfit().doubleValue(), 1e-9);
    }

    @Test
    void historicalRatesFilterEntryAndApplySignedFundingAtMarks() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        Indicator<Num> rates = PerpetualFuturesRecipes.historicalRates(series);
        Strategy strategy = PerpetualFuturesRecipes.fundingAwareStrategy(series, rates);
        assertTrue(strategy.shouldEnter(0));
        Indicator<Num> expensive = new FixedNumIndicator(series, 0.001, 0, 0, 0, 0);
        assertFalse(PerpetualFuturesRecipes.fundingAwareStrategy(series, expensive).shouldEnter(0));
        Indicator<Num> absent = new FixedNumIndicator(series, 0, 0, 0, 0, 0);
        assertFalse(PerpetualFuturesRecipes.fundingAwareStrategy(series, absent).shouldEnter(0));
        Indicator<Num> unknown = new ConstantIndicator<>(series, NaN.NaN);
        assertFalse(PerpetualFuturesRecipes.fundingAwareStrategy(series, unknown).shouldEnter(0));
        Indicator<Num> credit = new FixedNumIndicator(series, -0.001, 0, 0, 0, 0);
        assertTrue(PerpetualFuturesRecipes.fundingAwareStrategy(series, credit).shouldEnter(0));
        TradingRecord record = PerpetualFuturesRecipes.run(series, strategy, new ZeroCostModel(),
                PerpetualFuturesRecipes.historicalFunding(series, rates), 2, 1, new BigDecimal("0.001"));
        Position position = record.getPositions().getFirst();
        assertEquals(-0.3, position.getHoldingCost().doubleValue(), 1e-9);
        assertEquals(80.3, position.getProfit().doubleValue(), 1e-9);
    }

    @Test
    void contractLotConversionFloorsBaseAmountAndCanSkipEntry() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        Strategy strategy = PerpetualFuturesRecipes.fundingAwareStrategy(series,
                PerpetualFuturesRecipes.historicalRates(series));
        BigDecimal step = PerpetualFuturesRecipes.baseAmountIncrement("0.01", "0.1");
        TradingRecord rounded = PerpetualFuturesRecipes.run(series, strategy, new ZeroCostModel(), new ZeroCostModel(),
                0.0037, 1, step);
        assertEquals(0.003, rounded.getPositions().getFirst().getEntry().getAmount().doubleValue(), 1e-9);
        TradingRecord skipped = PerpetualFuturesRecipes.run(series, strategy, new ZeroCostModel(), new ZeroCostModel(),
                0.0004, 1, step);
        assertEquals(0, skipped.getPositionCount());
    }
}
