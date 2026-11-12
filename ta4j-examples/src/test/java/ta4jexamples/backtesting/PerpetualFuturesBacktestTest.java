/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.ReturnOnCapitalCriterion;
import org.ta4j.core.num.Num;

class PerpetualFuturesBacktestTest {

    @Test
    void fundingAndFeesReduceLeveragedProfitInQuoteCurrency() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 104, 98, 110, 107, 115);
        TradingRecord funded = PerpetualFuturesBacktest.run(series, 3, 0.0001);
        TradingRecord unfunded = PerpetualFuturesBacktest.run(series, 3, 0);
        TradingRecord unlevered = PerpetualFuturesBacktest.run(series, 1, 0.0001);
        assertEquals(1, funded.getPositionCount());
        Position position = funded.getPositions().getFirst();
        double amount = position.getEntry().getAmount().doubleValue();
        assertEquals(299.85, amount, 1e-8);
        // Settlements at 08:00 and 16:00; the entry settlement is excluded.
        double funding = amount * 0.0001 * (98 + 107);
        double fees = amount * 0.0005 * (100 + 115);
        double expectedProfit = amount * (115 - 100) - fees - funding;
        Num profit = new NetProfitLossCriterion().calculate(series, funded);
        assertEquals(expectedProfit, profit.doubleValue(), 1e-8);
        assertEquals(funding, position.getHoldingCost().doubleValue(), 1e-8);
        assertEquals(funding, unfunded.getPositions().getFirst().getProfit().minus(profit).doubleValue(), 1e-8);
        assertTrue(profit.isGreaterThan(unlevered.getPositions().getFirst().getProfit()));
        assertEquals(expectedProfit / 10_000 * 100,
                new ReturnOnCapitalCriterion(10_000, ReturnRepresentation.PERCENTAGE).calculate(series, funded)
                        .doubleValue(),
                1e-8);
        // Drawdown follows the interim fall from 104 to 98, including its funding.
        Num drawdown = new MaximumAbsoluteDrawdownCriterion().calculate(series, funded);
        assertTrue(Num.isFinite(drawdown));
        assertEquals(amount * (6 + 98 * 0.0001), drawdown.doubleValue(), 1e-8);
    }

    @Test
    void maintenanceMarginStopClosesBeforeTheScheduledExit() {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 90, 67, 110);
        TradingRecord record = PerpetualFuturesBacktest.run(series, 3, 0.0001);
        Position position = record.getPositions().getFirst();
        assertEquals(2, position.getExit().getIndex());
        assertEquals(67, position.getExit().getPricePerAsset().doubleValue(), 1e-8);
        assertTrue(position.getProfit().isNegative());
    }
}
