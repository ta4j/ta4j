/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.portfolio;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.criteria.EnterAndHoldCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetReturnCriterion;
import org.ta4j.core.portfolio.PortfolioAllocation;
import org.ta4j.core.portfolio.PortfolioExecutionResult;
import org.ta4j.core.portfolio.PortfolioSeries;
import org.ta4j.core.portfolio.PortfolioSeriesManager;
import org.ta4j.core.portfolio.PortfolioSnapshot.RebalanceStatus;
import org.ta4j.core.portfolio.RebalancePolicy;

/**
 * Static target-weight portfolio backtest: buy-and-hold versus monthly
 * rebalancing of a 60% equity / 30% bond / 10% cash allocation.
 *
 * <p>
 * Accounting assumptions: fractional long-only holdings, trades and valuation
 * at the aligned close, one common quote currency (adjust prices upstream),
 * uninvested weight held as cash, and strict end-time alignment without
 * forward fill. Prices are deterministic synthetic data so the output is
 * reproducible.
 * </p>
 */
public final class StaticPortfolioBacktest {

    private static final Logger LOG = LogManager.getLogger(StaticPortfolioBacktest.class);

    private StaticPortfolioBacktest() {
    }

    public static void main(String[] args) {
        PortfolioSeries portfolio = new PortfolioSeries(series("EQUITY", 100, 0.004, 0.08),
                series("BONDS", 50, 0.0005, 0.01));
        PortfolioSeriesManager manager = new PortfolioSeriesManager(portfolio, new LinearTransactionCostModel(0.001));
        Map<String, Double> targetWeights = new LinkedHashMap<>(); // the 10% remainder stays in cash
        targetWeights.put("EQUITY", 0.6);
        targetWeights.put("BONDS", 0.3);
        PortfolioAllocation allocation = new PortfolioAllocation(targetWeights);
        LOG.info("{}", portfolio);
        LOG.info("{}", allocation);

        // run(...) without a policy is buy-and-hold: invest at the first aligned bar, then hold.
        PortfolioExecutionResult buyAndHold = manager.run(allocation, 10_000);
        // A policy schedules every trade, including the initial investment; firstBarOf selects bar 0 too.
        PortfolioExecutionResult monthly = manager.run(allocation, 10_000,
                RebalancePolicy.firstBarOf(ChronoUnit.MONTHS, ZoneOffset.UTC));
        LOG.info("Buy and hold: {}", buyAndHold);
        LOG.info("Monthly:      {}", monthly);
        monthly.getSnapshots(RebalanceStatus.COMPLETED)
                .forEach(snapshot -> LOG.info("Rebalanced {}: turnover={}, cost={}", snapshot.getEndTime(),
                        snapshot.getTurnover(), snapshot.getTransactionCost()));

        // The value series starts at the initial cash, so existing criteria include the initial fees.
        BarSeries equityCurve = monthly.toPortfolioValueSeries("Monthly 60/30/10");
        LOG.info("Net return from criteria: {} (result: {})",
                new EnterAndHoldCriterion(new NetReturnCriterion(ReturnRepresentation.DECIMAL))
                        .calculate(equityCurve, new BaseTradingRecord()),
                monthly.getTotalReturn());
        LOG.info("Maximum drawdown: {}",
                new EnterAndHoldCriterion(new MaximumDrawdownCriterion()).calculate(equityCurve,
                        new BaseTradingRecord()));
    }

    /** Daily closes following a drifting sine wave over three calendar months. */
    private static BarSeries series(String name, double start, double dailyDrift, double amplitude) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Instant firstEnd = Instant.parse("2026-01-02T00:00:00Z");
        for (int day = 0; day < 90; day++) {
            double close = start * (1 + dailyDrift * day + amplitude * Math.sin(day / 9.0));
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(firstEnd.plus(Duration.ofDays(day)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(0)
                    .add();
        }
        return series;
    }
}
