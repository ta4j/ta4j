/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.backtesting;

import java.math.BigDecimal;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Position;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.FundingCostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.backtest.PositionSizer;
import org.ta4j.core.backtest.TradeOnCurrentCloseModel;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.drawdown.MaximumAbsoluteDrawdownCriterion;
import org.ta4j.core.criteria.pnl.NetProfitLossCriterion;
import org.ta4j.core.criteria.pnl.ReturnOnCapitalCriterion;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedNumIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.UnderIndicatorRule;

/**
 * Offline recipes for comparing spot and a linear perpetual. Prices, capital,
 * fees, funding and profit are in quote currency; quantities are base units.
 * The sample funding rates are historical observations, not forecasts. The
 * comparison does not simulate margin calls, liquidation, or exchange rounding
 * beyond the illustrated quantity increment.
 *
 * @since 0.28.1
 */
public final class PerpetualFuturesRecipes {
    private static final Logger LOG = LogManager.getLogger(PerpetualFuturesRecipes.class);

    private PerpetualFuturesRecipes() {
    }

    /**
     * Runs the recipes with synthetic data, without network access.
     *
     * @param args unused
     * @since 0.28.1
     */
    public static void main(String[] args) {
        BarSeries series = PerpetualFuturesBacktest.createSeries(100, 110, 120, 130, 140);
        Indicator<Num> rates = historicalRates(series);
        Strategy strategy = fundingAwareStrategy(series, rates);
        CostModel fees = new MinimumCommission(0.001, 0.25);
        BigDecimal step = baseAmountIncrement("0.01", "0.1");
        double capital = 1_000;
        TradingRecord spot = run(series, strategy, fees, new ZeroCostModel(), 2, 1, step);
        TradingRecord perpetual = run(series, strategy, fees, historicalFunding(series, rates), 2, 3, step);
        report("Spot", series, spot, capital);
        report("Perpetual", series, perpetual, capital);
    }

    // Values must be aligned to the price bars; zero means no settlement.
    static Indicator<Num> historicalRates(BarSeries series) {
        return new FixedNumIndicator(series, 0.0005, 0.001, 0, -0.002, 0);
    }

    static CostModel historicalFunding(BarSeries series, Indicator<Num> rates) {
        Indicator<Num> marks = new ClosePriceIndicator(series);
        return new FundingCostModel(rates, marks);
    }

    static Strategy fundingAwareStrategy(BarSeries series, Indicator<Num> rates) {
        // Only enter on the chosen signal when the observed rate is below 0.1%.
        Rule entry = new FixedRule(series.getBeginIndex()).and(new UnderIndicatorRule(rates, 0.001));
        return new BaseStrategy(entry, new FixedRule(series.getEndIndex()));
    }

    // Contract size is base units per contract; lot increment is contracts per
    // step.
    static BigDecimal baseAmountIncrement(String contractSize, String contractLotIncrement) {
        return new BigDecimal(contractSize).multiply(new BigDecimal(contractLotIncrement));
    }

    static PositionSizer lotSizer(double baseAmount, double leverage, BigDecimal baseIncrement) {
        return PositionSizer.fixed(baseAmount).leveraged(leverage).roundedDownTo(baseIncrement);
    }

    static TradingRecord run(BarSeries series, Strategy strategy, CostModel fees, CostModel funding, double baseAmount,
            double leverage, BigDecimal increment) {
        return new BarSeriesManager(series, fees, funding, new TradeOnCurrentCloseModel()).run(strategy,
                lotSizer(baseAmount, leverage, increment));
    }

    static Num drawdownAsShareOfCapital(BarSeries series, TradingRecord record, Number capital) {
        return new MaximumAbsoluteDrawdownCriterion().calculate(series, record)
                .dividedBy(series.numFactory().numOf(capital));
    }

    private static void report(String label, BarSeries series, TradingRecord record, double capital) {
        Position position = record.getPositions().getFirst();
        LOG.info("{}: committed capital={} quote, entry size={} base", label, capital, position.getEntry().getAmount());
        LOG.info("{}: fees={} quote, funding={} quote, net profit={} quote", label,
                position.getEntry().getCost().plus(position.getExit().getCost()), position.getHoldingCost(),
                new NetProfitLossCriterion().calculate(series, record));
        LOG.info("{}: return on capital={}%, maximum drawdown/capital={} (fraction)", label,
                new ReturnOnCapitalCriterion(capital, ReturnRepresentation.PERCENTAGE).calculate(series, record),
                drawdownAsShareOfCapital(series, record, capital));
    }

    /** Minimum commission per executed trade, not per position or bar. */
    static final class MinimumCommission implements CostModel {
        private final double rate;
        private final double minimum;

        MinimumCommission(double rate, double minimum) {
            this.rate = rate;
            this.minimum = minimum;
        }

        @Override
        public Num calculate(Num price, Num amount) {
            if (amount.isZero()) {
                return amount.getNumFactory().zero();
            }
            Num proportional = price.multipliedBy(amount).multipliedBy(amount.getNumFactory().numOf(rate));
            Num floor = amount.getNumFactory().numOf(minimum);
            return proportional.isLessThan(floor) ? floor : proportional;
        }

        @Override
        public Num calculate(Position position, int finalIndex) {
            return calculate(position);
        }

        @Override
        public Num calculate(Position position) {
            Trade entry = position.getEntry();
            if (entry == null) {
                return null;
            }
            Num cost = entry.getCost();
            return position.getExit() == null ? cost : cost.plus(position.getExit().getCost());
        }

        @Override
        public boolean equals(CostModel other) {
            return other instanceof MinimumCommission commission && rate == commission.rate
                    && minimum == commission.minimum;
        }
    }
}
