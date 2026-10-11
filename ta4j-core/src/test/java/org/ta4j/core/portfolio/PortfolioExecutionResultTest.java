/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.portfolio.PortfolioFixtures.START;
import static org.ta4j.core.portfolio.PortfolioFixtures.assertNumClose;
import static org.ta4j.core.portfolio.PortfolioFixtures.series;

import java.time.Duration;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.criteria.EnterAndHoldCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;

public class PortfolioExecutionResultTest {

    @Test
    public void valueSeriesStartsWithInitialCapitalSoInitialCostsReconcile() {
        PortfolioExecutionResult result = flatRunWithOnePercentCost(100, 100);

        BarSeries valueSeries = result.toPortfolioValueSeries("portfolio");

        assertEquals(3, valueSeries.getBarCount());
        assertEquals(START.minus(Duration.ofDays(1)), valueSeries.getFirstBar().getEndTime());
        assertNumEquals(1000, valueSeries.getFirstBar().getClosePrice());
        assertEquals(result.getSnapshots().get(1).getEndTime(), valueSeries.getBar(2).getEndTime());
        Num exportedReturn = new EnterAndHoldCriterion(new NetReturnCriterion(ReturnRepresentation.DECIMAL))
                .calculate(valueSeries, new BaseTradingRecord());
        assertNumEquals(result.getTotalReturn(), exportedReturn, 1e-12);
        assertNumClose(-0.00990099, result.getTotalReturn(), 1e-6);
        Num drawdown = new EnterAndHoldCriterion(new MaximumDrawdownCriterion()).calculate(valueSeries,
                new BaseTradingRecord());
        assertNumClose(0.00990099, drawdown, 1e-6);
    }

    @Test
    public void oneBarRunStillExposesTheInitialInterval() {
        PortfolioExecutionResult result = flatRunWithOnePercentCost(100);

        BarSeries valueSeries = result.toPortfolioValueSeries();

        assertEquals("Portfolio value", valueSeries.getName());
        assertEquals(2, valueSeries.getBarCount());
        assertNumEquals(result.getInitialCash(), valueSeries.getFirstBar().getClosePrice());
        assertNumEquals(result.getFinalValue(), valueSeries.getLastBar().getClosePrice());
    }

    @Test
    public void aggregatesCostsTradesAndRebalanceOutcomes() {
        PortfolioSeriesManager manager = new PortfolioSeriesManager(
                new PortfolioSeries(series("ALPHA", 100, 200, 200), series("BETA", 100, 50, 50)));

        PortfolioExecutionResult result = manager.run(new PortfolioAllocation(Map.of("ALPHA", 0.5, "BETA", 0.5)), 1000,
                RebalancePolicy.onIndexes(0, 1));

        assertNumEquals(1750, result.getTotalTradedNotional());
        assertNumEquals(1.6, result.getTotalTurnover());
        assertNumEquals(0, result.getTotalTransactionCost());
        assertEquals(2, result.getRebalanceCount(RebalanceStatus.COMPLETED));
        assertEquals(1, result.getRebalanceCount(RebalanceStatus.NOT_SCHEDULED));
        assertEquals(2, result.getSnapshots(RebalanceStatus.COMPLETED).size());
    }

    @Test
    public void toStringIsACompactSummary() {
        PortfolioSeriesManager manager = new PortfolioSeriesManager(
                new PortfolioSeries(series("ALPHA", 100, 110), series("BETA", 50, 50)));

        PortfolioExecutionResult result = manager.run(new PortfolioAllocation(Map.of("ALPHA", 0.5)), 1000);

        assertEquals("PortfolioExecutionResult{bars=2, from=2026-01-01T00:00:00Z, to=2026-01-02T00:00:00Z, "
                + "initialCash=1000, finalValue=1050, totalReturn=0.05 (5%), transactionCost=0, tradedNotional=500, "
                + "rebalances={completed=1, partial=0, skipped=0}, "
                + "finalWeights={ALPHA=0.52381, BETA=0, cash=0.47619}}", result.toString());
    }

    private static PortfolioExecutionResult flatRunWithOnePercentCost(double... closes) {
        PortfolioSeriesManager manager = new PortfolioSeriesManager(
                new PortfolioSeries(series("ALPHA", closes), series("BETA", closes)),
                new LinearTransactionCostModel(0.01));
        return manager.run(new PortfolioAllocation(Map.of("ALPHA", 0.5, "BETA", 0.5)), 1000);
    }
}
