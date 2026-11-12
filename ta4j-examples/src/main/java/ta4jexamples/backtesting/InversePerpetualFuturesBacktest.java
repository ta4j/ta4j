/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.time.Duration;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.FundingCostModel;
import org.ta4j.core.analysis.cost.LinearTransactionCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.ReturnOnCapitalCriterion;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.FixedRule;

/**
 * Offline inverse-contract recipe without a separate core valuation model. An
 * inverse long of N quote-currency units is a short of amount N at reciprocal
 * prices: its gross profit is N * (1 / entryPrice - 1 / exitPrice), in base
 * coin. Venue contract counts must first be multiplied by their quote-unit face
 * value.
 *
 * <p>
 * Reciprocal prices are base coin per quote unit, so capital, fees, funding and
 * drawdown are also in base coin. Positive venue funding charges the economic
 * long: the rate must be negated because the transformed position is a short.
 * High and low prices exchange roles under reciprocation. Volume is copied only
 * as source metadata, not converted into a contract quantity.
 *
 * <p>
 * The synthetic current-close backtest uses closes as marks and a fixed funding
 * schedule. It does not model a margin account, liquidation, collateral changes
 * or exchange-specific settlement and fee conventions.
 *
 * @since 0.28.1
 */
public final class InversePerpetualFuturesBacktest {

    private static final Logger LOG = LogManager.getLogger(InversePerpetualFuturesBacktest.class);
    static final double CAPITAL = 0.5;
    static final double LEVERAGE = 3;
    static final double FEE_RATE = 0.0005;
    static final double FUNDING_RATE = 0.0001;

    private InversePerpetualFuturesBacktest() {
    }

    /**
     * Runs the bundled synthetic example without network access or credentials.
     *
     * @param args unused
     * @since 0.28.1
     */
    public static void main(String[] args) {
        BarSeries prices = PerpetualFuturesBacktest.createSeries(20_000, 21_000, 18_000, 22_000, 24_000, 25_000);
        BarSeries reciprocal = reciprocalSeries(prices);
        PositionSizer sizing = PositionSizer.balance(CAPITAL).leveraged(LEVERAGE).roundedDownTo(1);
        TradingRecord record = run(reciprocal, sizing, FUNDING_RATE);
        LOG.info("Inverse perpetual: base-coin capital={}, leverage={}x", CAPITAL, LEVERAGE);
        LOG.info("Net profit (base coin): {}", new NetProfitLossCriterion().calculate(reciprocal, record));
        LOG.info("Return on capital (%): {}",
                new ReturnOnCapitalCriterion(CAPITAL, ReturnRepresentation.PERCENTAGE).calculate(reciprocal, record));
        LOG.info("Maximum absolute drawdown (base coin): {}",
                new MaximumAbsoluteDrawdownCriterion().calculate(reciprocal, record));
    }

    static TradingRecord run(BarSeries reciprocal, PositionSizer sizing, double venueFundingRate) {
        Strategy strategy = new BaseStrategy(new FixedRule(reciprocal.getBeginIndex()),
                new FixedRule(reciprocal.getEndIndex()));
        LinearTransactionCostModel fees = new LinearTransactionCostModel(FEE_RATE);
        FundingCostModel funding = FundingCostModel.fixedRate(reciprocal, -venueFundingRate, Duration.ofHours(8));
        return new BarSeriesManager(reciprocal, fees, funding, new TradeOnCurrentCloseModel()).run(strategy,
                TradeType.SELL, sizing);
    }

    // Copies retained positive-price bars into a new, zero-based reciprocal series.
    static BarSeries reciprocalSeries(BarSeries source) {
        BarSeries reciprocal = new BaseBarSeriesBuilder().withName("synthetic inverse perpetual")
                .withNumFactory(source.numFactory())
                .build();
        Num one = source.numFactory().one();
        for (int index = source.getBeginIndex(); index <= source.getEndIndex(); index++) {
            Bar bar = source.getBar(index);
            reciprocal.barBuilder()
                    .timePeriod(bar.getTimePeriod())
                    .endTime(bar.getEndTime())
                    .openPrice(one.dividedBy(bar.getOpenPrice()))
                    .highPrice(one.dividedBy(bar.getLowPrice()))
                    .lowPrice(one.dividedBy(bar.getHighPrice()))
                    .closePrice(one.dividedBy(bar.getClosePrice()))
                    .volume(bar.getVolume())
                    .add();
        }
        return reciprocal;
    }
}
