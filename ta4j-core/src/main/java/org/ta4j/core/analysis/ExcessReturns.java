/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Duration;
import java.util.Objects;

import org.ta4j.core.utils.BarSeriesUtils;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.TimeConstants;

/**
 * Computes compounded excess returns between sampled index pairs.
 *
 * <p>
 * For each sampled pair, the excess return is formed by compounding the per-bar
 * excess growth factors between the indices. This ensures mixed
 * in/out-of-market segments within the sampling window contribute
 * proportionally.
 *
 * <p>
 * The {@link CashReturnPolicy} defines how flat equity intervals are treated
 * relative to the risk-free benchmark, allowing flat segments to be neutral or
 * to incur underperformance against cash.
 *
 * @since 0.22.2
 */
public final class ExcessReturns {

    /**
     * Describes how flat equity intervals are treated when computing excess
     * returns.
     *
     * @since 0.22.2
     */
    public enum CashReturnPolicy {
        /**
         * Treats flat equity while out of the market as earning the risk-free rate, so
         * those intervals do not contribute to excess return underperformance.
         */
        CASH_EARNS_RISK_FREE,
        /**
         * Treats flat equity while out of the market as earning zero return, so those
         * intervals underperform the risk-free benchmark.
         */
        CASH_EARNS_ZERO
    }

    private final Num annualRiskFreeRate;
    private final CashReturnPolicy cashReturnPolicy;
    private final BarSeries series;
    private final InvestedInterval investedInterval;
    private final CashFlow cashFlow;

    /**
     * Creates an excess return calculator with invested interval detection from a
     * trading record.
     *
     * @param series             the bar series providing time deltas and num
     *                           factory
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @param cashReturnPolicy   the policy for flat equity intervals
     * @param tradingRecord      the trading record used to detect invested
     *                           intervals
     * @since 0.22.2
     */
    public ExcessReturns(BarSeries series, Num annualRiskFreeRate, CashReturnPolicy cashReturnPolicy,
            TradingRecord tradingRecord) {
        this(series, annualRiskFreeRate, cashReturnPolicy, tradingRecord, OpenPositionHandling.MARK_TO_MARKET);
    }

    /**
     * Creates an excess return calculator with invested interval detection from a
     * trading record.
     *
     * @param series               the bar series providing time deltas and num
     *                             factory
     * @param annualRiskFreeRate   the annual risk-free rate (e.g. 0.05 for 5%)
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param tradingRecord        the trading record used to detect invested
     *                             intervals
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public ExcessReturns(BarSeries series, Num annualRiskFreeRate, CashReturnPolicy cashReturnPolicy,
            TradingRecord tradingRecord, OpenPositionHandling openPositionHandling) {
        this(series, annualRiskFreeRate, cashReturnPolicy, tradingRecord, EquityCurveMode.MARK_TO_MARKET,
                openPositionHandling);
    }

    /**
     * Creates an excess return calculator with invested interval detection from a
     * trading record.
     *
     * @param series               the bar series providing time deltas and num
     *                             factory
     * @param annualRiskFreeRate   the annual risk-free rate (e.g. 0.05 for 5%)
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param tradingRecord        the trading record used to detect invested
     *                             intervals
     * @param equityCurveMode      the cash flow calculation mode
     * @param openPositionHandling how open positions should be handled
     * @since 0.22.2
     */
    public ExcessReturns(BarSeries series, Num annualRiskFreeRate, CashReturnPolicy cashReturnPolicy,
            TradingRecord tradingRecord, EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(series, annualRiskFreeRate, cashReturnPolicy,
                new InvestedInterval(series, tradingRecord,
                        equityCurveMode == EquityCurveMode.REALIZED ? OpenPositionHandling.IGNORE
                                : openPositionHandling),
                new CashFlow(series, tradingRecord, equityCurveMode, openPositionHandling));
    }

    /**
     * Creates excess returns for one position. Native futures use the existing
     * entry-notional capital fallback rather than requiring account capital.
     *
     * @param series               the bar series
     * @param annualRiskFreeRate   the annual risk-free rate
     * @param cashReturnPolicy     the policy for flat equity intervals
     * @param position             the position to analyse
     * @param equityCurveMode      the equity curve mode
     * @param openPositionHandling how to handle remaining exposure
     * @since 0.25.1
     */
    public ExcessReturns(BarSeries series, Num annualRiskFreeRate, CashReturnPolicy cashReturnPolicy, Position position,
            EquityCurveMode equityCurveMode, OpenPositionHandling openPositionHandling) {
        this(series, annualRiskFreeRate, cashReturnPolicy,
                new InvestedInterval(series, new BaseTradingRecord(Objects.requireNonNull(position, "position")),
                        equityCurveMode == EquityCurveMode.REALIZED ? OpenPositionHandling.IGNORE
                                : openPositionHandling),
                new CashFlow(series, position,
                        openPositionHandling == OpenPositionHandling.IGNORE ? EquityCurveMode.REALIZED
                                : equityCurveMode));
    }

    private ExcessReturns(BarSeries series, Num annualRiskFreeRate, CashReturnPolicy cashReturnPolicy,
            InvestedInterval investedInterval, CashFlow cashFlow) {
        this.series = Objects.requireNonNull(series, "series cannot be null");
        this.annualRiskFreeRate = Objects.requireNonNull(annualRiskFreeRate, "annualRiskFreeRate cannot be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy cannot be null");
        this.investedInterval = investedInterval;
        this.cashFlow = cashFlow;
    }

    /**
     * Whether the curve has a first-bar futures return from initial capital, rather
     * than cumulative activity before the retained window.
     *
     * @return whether sampling should include the capital-to-first-bar move
     * @since 0.25.1
     */
    public boolean hasInitialReturn() {
        return cashFlow.hasInitialReturn();
    }

    /**
     * Computes the compounded excess return using the configured cash flow.
     *
     * @param previousIndex the start index; one before the series begin index uses
     *                      initial capital when {@link #hasInitialReturn()} is true
     * @param currentIndex  the end index
     * @return the compounded excess return
     * @since 0.22.2
     */
    public Num excessReturn(int previousIndex, int currentIndex) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        Num one = numFactory.one();
        if (currentIndex <= previousIndex) {
            return zero;
        }

        Num excessGrowth = one;
        for (int i = previousIndex + 1; i <= currentIndex; i++) {
            Num previousEquity = cashFlow.getValue(i - 1);
            Num currentEquity = cashFlow.getValue(i);
            Num riskFreeGrowth = riskFreeGrowth(i - 1, i, one);
            boolean isFlat = currentEquity.isEqual(previousEquity);
            boolean isInvested = isInvested(i);
            if (cashReturnPolicy == CashReturnPolicy.CASH_EARNS_RISK_FREE && isFlat && !isInvested) {
                continue;
            }
            if (previousEquity.isZero()) {
                if (!currentEquity.isZero()) {
                    excessGrowth = zero;
                }
                continue;
            }

            if (riskFreeGrowth.isZero()) {
                excessGrowth = excessGrowth.multipliedBy(currentEquity.dividedBy(previousEquity));
                continue;
            }

            Num growth = currentEquity.dividedBy(previousEquity).dividedBy(riskFreeGrowth);
            excessGrowth = excessGrowth.multipliedBy(growth);
        }

        return excessGrowth.minus(one);
    }

    private Num riskFreeGrowth(int previousIndex, int currentIndex, Num one) {
        NumFactory numFactory = series.numFactory();
        Num zero = numFactory.zero();
        Num deltaYears;
        if (previousIndex == series.getBeginIndex() - 1 && hasInitialReturn()) {
            long seconds = Math
                    .max(0, Duration
                            .between(series.getBar(series.getBeginIndex()).getBeginTime(),
                                    series.getBar(currentIndex).getEndTime())
                            .getSeconds());
            deltaYears = numFactory.numOf(seconds).dividedBy(numFactory.numOf(TimeConstants.SECONDS_PER_YEAR));
        } else {
            deltaYears = BarSeriesUtils.deltaYears(series, previousIndex, currentIndex);
        }
        if (deltaYears.isLessThanOrEqual(zero)) {
            return one;
        }
        return one.plus(annualRiskFreeRate).pow(deltaYears);
    }

    private boolean isInvested(int index) {
        return investedInterval.getValue(index);
    }

}
