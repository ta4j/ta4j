/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;
import java.time.Instant;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FundingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.ReturnOnCapitalCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.StopLossRule;

/**
 * Offline linear perpetual backtest: amounts are base-asset units and all
 * profits, fees, funding and capital are in the quote currency. Convert venue
 * contract counts to base units before using this recipe.
 *
 * <p>
 * The maintenance-margin stop is an approximation, not an exchange liquidation
 * engine. It ignores margin tiers, collateral haircuts and liquidation fees.
 * Current-close execution makes this small example deterministic; real stops
 * can slip or gap beyond the modeled threshold. Funding uses closes as marks
 * and a fixed UTC-aligned schedule, not a prediction of exchange settlements.
 *
 * @since 0.28.1
 */
public final class PerpetualFuturesBacktest {

    private static final Logger LOG = LogManager.getLogger(PerpetualFuturesBacktest.class);
    static final double CAPITAL = 10_000;
    static final double LEVERAGE = 3;
    static final double MAINTENANCE_MARGIN_RATE = 0.005;
    static final double FEE_RATE = 0.0005;
    static final double FUNDING_RATE = 0.0001;

    private PerpetualFuturesBacktest() {
    }

    /**
     * Runs the bundled synthetic example without network access or credentials.
     *
     * @param args unused
     * @since 0.28.1
     */
    public static void main(String[] args) {
        BarSeries series = createSeries(100, 104, 98, 110, 107, 115);
        TradingRecord record = run(series, LEVERAGE, FUNDING_RATE);
        LOG.info("Linear perpetual: quote-currency capital={}, leverage={}x", CAPITAL, LEVERAGE);
        LOG.info("Net profit (quote currency): {}", new NetProfitLossCriterion().calculate(series, record));
        LOG.info("Return on capital (%): {}",
                new ReturnOnCapitalCriterion(CAPITAL, ReturnRepresentation.PERCENTAGE).calculate(series, record));
        LOG.info("Maximum absolute drawdown (quote currency): {}",
                new MaximumAbsoluteDrawdownCriterion().calculate(series, record));
    }

    static TradingRecord run(BarSeries series, double leverage, double fundingRate) {
        ClosePriceIndicator price = new ClosePriceIndicator(series);
        double liquidationLossPercentage = 100 * (1 / leverage - MAINTENANCE_MARGIN_RATE);
        // Close-only approximation: intrabar wicks through the level are missed,
        // and a triggered exit still fills at that bar's close.
        Rule liquidationStop = new StopLossRule(price, liquidationLossPercentage);
        Strategy strategy = new BaseStrategy(new FixedRule(series.getBeginIndex()),
                new FixedRule(series.getEndIndex()).or(liquidationStop));
        // Funding is a signed holding cost; positive rates charge this long.
        FundingCostModel funding = FundingCostModel.fixedRate(series, fundingRate, Duration.ofHours(8));
        LinearTransactionCostModel fees = new LinearTransactionCostModel(FEE_RATE);
        // Leverage scales exposure, not the capital denominator or a margin account.
        // balance() budgets the unlevered entry fee; leveraged fees still reduce P&L.
        PositionSizer sizing = PositionSizer.balance(CAPITAL).leveraged(leverage).roundedDownTo(0.001);
        return new BarSeriesManager(series, fees, funding, new TradeOnCurrentCloseModel()).run(strategy, sizing);
    }

    static BarSeries createSeries(double... prices) {
        BarSeries series = new BaseBarSeriesBuilder().withName("synthetic linear perpetual").build();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        for (int index = 0; index < prices.length; index++) {
            double price = prices[index];
            series.barBuilder()
                    .timePeriod(Duration.ofHours(4))
                    .endTime(start.plus(Duration.ofHours(4L * index)))
                    .openPrice(price)
                    .highPrice(price)
                    .lowPrice(price)
                    .closePrice(price)
                    .volume(1_000)
                    .add();
        }
        return series;
    }
}
