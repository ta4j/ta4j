/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.utils.TimeConstants;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

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
    private final BarTimes barTimes;

    /**
     * Captures attempted before giving up on a series that keeps evicting the
     * analysed window.
     */
    private static final int MAX_CAPTURE_ATTEMPTS = 8;

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
        this.series = Objects.requireNonNull(series, "series cannot be null");
        this.annualRiskFreeRate = Objects.requireNonNull(annualRiskFreeRate, "annualRiskFreeRate cannot be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy cannot be null");

        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(equityCurveMode, "equityCurveMode cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");

        OpenPositionHandling effectiveOpenPositionHandling = equityCurveMode == EquityCurveMode.REALIZED
                ? OpenPositionHandling.IGNORE
                : openPositionHandling;
        // Bar times are frozen together with the curves: a live series may
        // evict or replace bars afterwards, and the risk-free growth must stay
        // consistent with the captured cash flow.
        for (int attempt = 0; attempt < MAX_CAPTURE_ATTEMPTS; attempt++) {
            InvestedInterval invested = new InvestedInterval(series, tradingRecord, effectiveOpenPositionHandling);
            CashFlow flow = new CashFlow(series, tradingRecord, equityCurveMode, effectiveOpenPositionHandling);
            BarTimes times = series.withReadLock(() -> BarTimes.capture(series, flow.getBeginIndex()));
            if (times != null) {
                this.investedInterval = invested;
                this.cashFlow = flow;
                this.barTimes = times;
                return;
            }
        }
        throw new IllegalStateException(
                "Bar series '" + series.getName() + "' evicted the analysis window during each of "
                        + MAX_CAPTURE_ATTEMPTS + " attempts; retry once retention is stable");
    }

    /**
     * Computes the compounded excess return using the configured cash flow.
     *
     * @param previousIndex the start index
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
        for (long cursor = (long) previousIndex + 1L; cursor <= currentIndex; cursor++) {
            int i = (int) cursor;
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
        Num deltaYears = barTimes.deltaYears(previousIndex, currentIndex, numFactory);
        if (deltaYears.isLessThanOrEqual(zero)) {
            return one;
        }
        return one.plus(annualRiskFreeRate).pow(deltaYears);
    }

    private boolean isInvested(int index) {
        return investedInterval.getValue(index);
    }

    /**
     * End times of the bars retained when the curves were captured.
     *
     * @param beginIndex the absolute index of the first captured time
     * @param endTimes   the captured end times
     */
    private record BarTimes(int beginIndex, Instant[] endTimes) {

        /**
         * Reads the retained bars' end times; runs inside the series read scope.
         *
         * @return the times, or {@code null} if bars of the window starting at
         *         {@code windowBeginIndex} were evicted since it was captured
         */
        static BarTimes capture(BarSeries series, int windowBeginIndex) {
            if (series.isEmpty()) {
                return new BarTimes(0, new Instant[0]);
            }
            int beginIndex = series.getBeginIndex();
            if (beginIndex > windowBeginIndex) {
                return null;
            }
            Instant[] endTimes = new Instant[series.getEndIndex() - beginIndex + 1];
            for (int i = 0; i < endTimes.length; i++) {
                endTimes[i] = series.getBar(beginIndex + i).getEndTime();
            }
            return new BarTimes(beginIndex, endTimes);
        }

        /**
         * @return the years between two captured bar end times, or zero when either bar
         *         was not captured or time does not advance
         */
        Num deltaYears(int previousIndex, int currentIndex, NumFactory numFactory) {
            long previousOffset = (long) previousIndex - beginIndex;
            long currentOffset = (long) currentIndex - beginIndex;
            if (previousOffset < 0 || currentOffset < 0 || previousOffset >= endTimes.length
                    || currentOffset >= endTimes.length) {
                return numFactory.zero();
            }
            long seconds = Duration.between(endTimes[(int) previousOffset], endTimes[(int) currentOffset]).getSeconds();
            return seconds <= 0 ? numFactory.zero()
                    : numFactory.numOf(seconds).dividedBy(numFactory.numOf(TimeConstants.SECONDS_PER_YEAR));
        }
    }

}
