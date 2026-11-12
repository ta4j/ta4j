/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.ReturnOnCapitalCriterion;
import org.ta4j.core.num.Num;

class InversePerpetualFuturesBacktestTest {

    @Test
    void oneInverseContractMatchesItsBaseCoinPayoffAndFees() {
        BarSeries prices = PerpetualFuturesBacktest.createSeries(20_000, 25_000);
        BarSeries reciprocal = InversePerpetualFuturesBacktest.reciprocalSeries(prices);
        // One contract has a face value of 100 quote-currency units.
        TradingRecord record = InversePerpetualFuturesBacktest.run(reciprocal, PositionSizer.fixed(100), 0);
        Position position = record.getPositions().getFirst();
        double grossProfit = 100 * (1.0 / 20_000 - 1.0 / 25_000);
        double fees = 100 * 0.0005 * (1.0 / 20_000 + 1.0 / 25_000);
        assertTrue(position.getEntry().isSell());
        assertEquals(100, position.getEntry().getAmount().doubleValue(), 1e-12);
        assertEquals(grossProfit - fees, new NetProfitLossCriterion().calculate(reciprocal, record).doubleValue(),
                1e-12);
    }

    @Test
    void economicLongPaysPositiveFundingAndUsesBaseCoinCapital() {
        BarSeries prices = PerpetualFuturesBacktest.createSeries(20_000, 21_000, 18_000, 22_000, 24_000, 25_000);
        BarSeries reciprocal = InversePerpetualFuturesBacktest.reciprocalSeries(prices);
        PositionSizer sizing = PositionSizer.balance(0.5).leveraged(3).roundedDownTo(1);
        TradingRecord funded = InversePerpetualFuturesBacktest.run(reciprocal, sizing, 0.0001);
        TradingRecord unfunded = InversePerpetualFuturesBacktest.run(reciprocal, sizing, 0);
        TradingRecord credited = InversePerpetualFuturesBacktest.run(reciprocal, sizing, -0.0001);
        TradingRecord unlevered = InversePerpetualFuturesBacktest.run(reciprocal,
                PositionSizer.balance(0.5).roundedDownTo(1), 0.0001);
        Position position = funded.getPositions().getFirst();
        double amount = position.getEntry().getAmount().doubleValue();
        assertEquals(29_985, amount, 1e-8);
        double funding = amount * 0.0001 * (1.0 / 18_000 + 1.0 / 24_000);
        double fees = amount * 0.0005 * (1.0 / 20_000 + 1.0 / 25_000);
        double expectedProfit = amount * (1.0 / 20_000 - 1.0 / 25_000) - fees - funding;
        NetProfitLossCriterion criterion = new NetProfitLossCriterion();
        Num profit = criterion.calculate(reciprocal, funded);
        assertEquals(funding, position.getHoldingCost().doubleValue(), 1e-12);
        assertEquals(expectedProfit, profit.doubleValue(), 1e-12);
        assertEquals(funding, criterion.calculate(reciprocal, unfunded).minus(profit).doubleValue(), 1e-12);
        assertEquals(2 * funding, criterion.calculate(reciprocal, credited).minus(profit).doubleValue(), 1e-12);
        assertTrue(profit.isGreaterThan(criterion.calculate(reciprocal, unlevered)));
        Num capitalReturn = new ReturnOnCapitalCriterion(0.5, ReturnRepresentation.PERCENTAGE).calculate(reciprocal,
                funded);
        Num drawdown = new MaximumAbsoluteDrawdownCriterion().calculate(reciprocal, funded);
        assertEquals(expectedProfit / 0.5 * 100, capitalReturn.doubleValue(), 1e-10);
        assertEquals(amount * (1.0 / 18_000 - 1.0 / 21_000) + amount * 0.0001 / 18_000, drawdown.doubleValue(), 1e-12);
        assertTrue(Num.isFinite(profit));
        assertTrue(Num.isFinite(capitalReturn));
        assertTrue(Num.isFinite(drawdown));
    }

    @Test
    void reciprocalPricesReverseHighAndLow() {
        BarSeries prices = new BaseBarSeriesBuilder().build();
        prices.barBuilder()
                .timePeriod(Duration.ofHours(4))
                .endTime(Instant.parse("2026-01-01T00:00:00Z"))
                .openPrice(100)
                .highPrice(120)
                .lowPrice(80)
                .closePrice(110)
                .volume(1_000)
                .add();
        Bar original = prices.getBar(0);
        Bar transformed = InversePerpetualFuturesBacktest.reciprocalSeries(prices).getBar(0);
        assertEquals(1.0 / 100, transformed.getOpenPrice().doubleValue(), 1e-12);
        assertEquals(1.0 / 80, transformed.getHighPrice().doubleValue(), 1e-12);
        assertEquals(1.0 / 120, transformed.getLowPrice().doubleValue(), 1e-12);
        assertEquals(1.0 / 110, transformed.getClosePrice().doubleValue(), 1e-12);
        assertEquals(original.getEndTime(), transformed.getEndTime());
        assertEquals(original.getTimePeriod(), transformed.getTimePeriod());
    }
}
