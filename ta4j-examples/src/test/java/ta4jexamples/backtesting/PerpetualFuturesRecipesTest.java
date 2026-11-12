/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.FixedRule;

class PerpetualFuturesRecipesTest {

    @Test
    void minimumCommissionAppliesPerTradeButNotToZeroExposure() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        CostModel fees = new PerpetualFuturesRecipes.MinimumCommission(series.numFactory(), 0.001, 0.25);
        assertEquals(0, fees.calculate(series.numFactory().numOf(100), series.numFactory().zero()).doubleValue(), 1e-9);
        assertEquals(0.25, fees.calculate(series.numFactory().numOf(100), series.numFactory().one()).doubleValue(),
                1e-9);
        assertEquals(1, fees.calculate(series.numFactory().numOf(100), series.numFactory().numOf(10)).doubleValue(),
                1e-9);
        Indicator<Num> rates = PerpetualFuturesRecipes.historicalRates(series);
        Indicator<Num> signal = PerpetualFuturesRecipes.latestObservedRates(series, rates);
        TradingRecord record = PerpetualFuturesRecipes.run(series,
                PerpetualFuturesRecipes.fundingAwareStrategy(series, signal), fees, new ZeroCostModel(), 1, 1,
                new BigDecimal("0.001"));
        Position position = record.getPositions().getFirst();
        assertEquals(0.5, position.getEntry().getCost().plus(position.getExit().getCost()).doubleValue(), 1e-9);
        assertEquals(39.5, position.getProfit().doubleValue(), 1e-9);
    }

    @Test
    void minimumCommissionReturnsNumericZeroForAnEmptyPosition() {
        for (NumFactory nums : List.of(DecimalNumFactory.getInstance(), DoubleNumFactory.getInstance())) {
            BarSeries series = new BaseBarSeriesBuilder().withNumFactory(nums).build();
            CostModel fees = new PerpetualFuturesRecipes.MinimumCommission(series.numFactory(), 0.001, 0.25);
            CostModel funding = PerpetualFuturesRecipes.historicalFunding(series, new FixedIndicator<>(series));
            Position empty = new Position(TradeType.BUY, fees, funding);
            assertEquals(nums.zero(), fees.calculate(empty));
            assertEquals(nums.zero(), empty.getPositionCost());
            assertEquals(nums.zero(), empty.getPositionCost(0));
        }
    }

    @Test
    void historicalRatesFilterEntryAndApplySignedFundingAtMarks() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        Indicator<Num> rates = PerpetualFuturesRecipes.historicalRates(series);
        Indicator<Num> signal = PerpetualFuturesRecipes.latestObservedRates(series, rates);
        Strategy strategy = PerpetualFuturesRecipes.fundingAwareStrategy(series, signal);
        assertTrue(strategy.shouldEnter(0));
        assertFalse(strategy.shouldEnter(1));
        assertFalse(strategy.shouldEnter(2));
        assertTrue(strategy.shouldEnter(3));
        assertTrue(strategy.shouldEnter(4));
        TradingRecord record = PerpetualFuturesRecipes.run(series, strategy, new ZeroCostModel(),
                PerpetualFuturesRecipes.historicalFunding(series, rates), 2, 1, new BigDecimal("0.001"));
        Position position = record.getPositions().getFirst();
        assertEquals(-0.3, position.getHoldingCost().doubleValue(), 1e-9);
        assertEquals(80.3, position.getProfit().doubleValue(), 1e-9);
    }

    @Test
    void sparseSettlementsCarryOnlyIntoTheDecisionSignal() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140, 150, 160, 170);
        NumFactory nums = series.numFactory();
        Indicator<Num> settlements = new FixedIndicator<>(series, NaN.NaN, nums.numOf(0.0005), NaN.NaN, NaN.NaN,
                nums.numOf(0.002), NaN.NaN, nums.zero(), NaN.NaN);
        Indicator<Num> signal = PerpetualFuturesRecipes.latestObservedRates(series, settlements);
        Strategy guarded = PerpetualFuturesRecipes.fundingAwareStrategy(series, signal);
        assertFalse(guarded.shouldEnter(0));
        assertTrue(guarded.shouldEnter(1));
        assertTrue(guarded.shouldEnter(2));
        assertTrue(guarded.shouldEnter(3));
        assertFalse(guarded.shouldEnter(4));
        assertFalse(guarded.shouldEnter(5));
        assertTrue(guarded.shouldEnter(6));
        assertTrue(guarded.shouldEnter(7));

        Strategy delayed = new BaseStrategy(new FixedRule(2).and(guarded.getEntryRule()), guarded.getExitRule());
        TradingRecord record = PerpetualFuturesRecipes.run(series, delayed, new ZeroCostModel(),
                PerpetualFuturesRecipes.historicalFunding(series, settlements), 1, 1, new BigDecimal("0.001"));
        Position position = record.getPositions().getFirst();
        assertEquals(2, position.getEntry().getIndex());
        assertEquals(7, position.getExit().getIndex());
        assertEquals(0, position.getHoldingCost(3).doubleValue(), 1e-9);
        assertEquals(0.28, position.getHoldingCost(4).doubleValue(), 1e-9);
        assertEquals(0.28, position.getHoldingCost(5).doubleValue(), 1e-9);
        assertEquals(0.28, position.getHoldingCost(6).doubleValue(), 1e-9);
        assertEquals(0.28, position.getHoldingCost(7).doubleValue(), 1e-9);
        assertEquals(49.72, position.getProfit().doubleValue(), 1e-9);
    }

    @Test
    void sampleKeepsSpotAndPerpetualEconomics() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        Indicator<Num> settlements = PerpetualFuturesRecipes.historicalRates(series);
        Indicator<Num> signal = PerpetualFuturesRecipes.latestObservedRates(series, settlements);
        Strategy strategy = PerpetualFuturesRecipes.fundingAwareStrategy(series, signal);
        CostModel fees = new PerpetualFuturesRecipes.MinimumCommission(series.numFactory(), 0.001, 0.25);
        BigDecimal step = PerpetualFuturesRecipes.baseAmountIncrement("0.01", "0.1");
        TradingRecord spot = PerpetualFuturesRecipes.run(series, strategy, fees, new ZeroCostModel(), 2, 1, step);
        TradingRecord perpetual = PerpetualFuturesRecipes.run(series, strategy, fees,
                PerpetualFuturesRecipes.historicalFunding(series, settlements), 2, 3, step);
        assertEquals(79.47, spot.getPositions().getFirst().getProfit().doubleValue(), 1e-9);
        assertEquals(239.46, perpetual.getPositions().getFirst().getProfit().doubleValue(), 1e-9);
    }

    @Test
    void contractLotConversionFloorsBaseAmountAndCanSkipEntry() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        Indicator<Num> rates = PerpetualFuturesRecipes.historicalRates(series);
        Strategy strategy = PerpetualFuturesRecipes.fundingAwareStrategy(series,
                PerpetualFuturesRecipes.latestObservedRates(series, rates));
        BigDecimal step = PerpetualFuturesRecipes.baseAmountIncrement("0.01", "0.1");
        TradingRecord rounded = PerpetualFuturesRecipes.run(series, strategy, new ZeroCostModel(), new ZeroCostModel(),
                0.0037, 1, step);
        assertEquals(0.003, rounded.getPositions().getFirst().getEntry().getAmount().doubleValue(), 1e-9);
        TradingRecord skipped = PerpetualFuturesRecipes.run(series, strategy, new ZeroCostModel(), new ZeroCostModel(),
                0.0004, 1, step);
        assertEquals(0, skipped.getPositionCount());
    }
}
