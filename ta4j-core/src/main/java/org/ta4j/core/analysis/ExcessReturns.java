/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.analysis;

import java.util.Objects;

import org.ta4j.core.utils.BarSeriesUtils;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

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
        this.series = Objects.requireNonNull(series, "series cannot be null");
        this.annualRiskFreeRate = Objects.requireNonNull(annualRiskFreeRate, "annualRiskFreeRate cannot be null");
        this.cashReturnPolicy = Objects.requireNonNull(cashReturnPolicy, "cashReturnPolicy cannot be null");

        Objects.requireNonNull(tradingRecord, "tradingRecord cannot be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling cannot be null");

        this.investedInterval = new InvestedInterval(series, tradingRecord, openPositionHandling);
        this.cashFlow = new CashFlow(series, tradingRecord, openPositionHandling);
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
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        var one = numFactory.one();
        if (currentIndex <= previousIndex) {
            return zero;
        }

        var excessGrowth = one;
        for (var i = previousIndex + 1; i <= currentIndex; i++) {
            var previousEquity = cashFlow.getValue(i - 1);
            var currentEquity = cashFlow.getValue(i);
            var riskFreeGrowth = riskFreeGrowth(i - 1, i, one);
            var isFlat = currentEquity.isEqual(previousEquity);
            var isInvested = isInvested(i);
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

            var growth = currentEquity.dividedBy(previousEquity).dividedBy(riskFreeGrowth);
            excessGrowth = excessGrowth.multipliedBy(growth);
        }

        return excessGrowth.minus(one);
    }

    private Num riskFreeGrowth(int previousIndex, int currentIndex, Num one) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        var deltaYears = BarSeriesUtils.deltaYears(series, previousIndex, currentIndex);
        if (deltaYears.isLessThanOrEqual(zero)) {
            return one;
        }
        return one.plus(annualRiskFreeRate).pow(deltaYears);
    }

    private boolean isInvested(int index) {
        return investedInterval.getValue(index);
    }

}
