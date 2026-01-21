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

import java.time.Duration;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Computes compounded excess returns between sampled index pairs.
 *
 * <p>
 * For each sampled pair, the excess return is formed by compounding the per-bar
 * excess growth factors between the indices. This ensures mixed in/out-of-market
 * segments within the sampling window contribute proportionally.
 *
 * <p>
 * The {@link CashReturnPolicy} defines how flat equity intervals are treated
 * relative to the risk-free benchmark, allowing flat segments to be neutral or
 * to incur underperformance against cash.
 *
 * @since 0.22.2
 */
public final class ExcessReturns {

    private static final double SECONDS_PER_YEAR = 365.2425d * 24 * 3600;

    /**
     * Describes how flat equity intervals are treated when computing excess returns.
     *
     * @since 0.22.2
     */
    public enum CashReturnPolicy {
        CASH_EARNS_RISK_FREE,
        CASH_EARNS_ZERO
    }

    private final CashReturnPolicy cashReturnPolicy;
    private final double annualRiskFreeRate;
    private final BarSeries series;

    /**
     * Creates an excess return calculator with a zero annual risk-free rate and
     * benchmark-neutral cash treatment.
     *
     * @param series the bar series providing time deltas and num factory
     * @since 0.22.2
     */
    public ExcessReturns(BarSeries series) {
        this(series, 0.0d, CashReturnPolicy.CASH_EARNS_RISK_FREE);
    }

    /**
     * Creates an excess return calculator.
     *
     * @param series the bar series providing time deltas and num factory
     * @param annualRiskFreeRate the annual risk-free rate (e.g. 0.05 for 5%)
     * @param cashReturnPolicy the policy for flat equity intervals
     * @since 0.22.2
     */
    public ExcessReturns(BarSeries series, double annualRiskFreeRate, CashReturnPolicy cashReturnPolicy) {
        this.series = series;
        this.annualRiskFreeRate = annualRiskFreeRate;
        this.cashReturnPolicy = cashReturnPolicy;
    }

    /**
     * Computes the compounded excess return between two sampled indices.
     *
     * @param cashFlow the equity curve cash flow
     * @param previousIndex the start index
     * @param currentIndex the end index
     * @return the compounded excess return
     * @since 0.22.2
     */
    public Num excessReturn(CashFlow cashFlow, int previousIndex, int currentIndex) {
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
            if (cashReturnPolicy == CashReturnPolicy.CASH_EARNS_RISK_FREE && isFlat) {
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
        var annual = numFactory.numOf(annualRiskFreeRate);
        var deltaYears = deltaYears(previousIndex, currentIndex);
        if (deltaYears.isLessThanOrEqual(zero)) {
            return one;
        }
        return one.plus(annual).pow(deltaYears);
    }

    private Num deltaYears(int previousIndex, int currentIndex) {
        var endPrev = series.getBar(previousIndex).getEndTime();
        var endNow = series.getBar(currentIndex).getEndTime();
        var seconds = Math.max(0, Duration.between(endPrev, endNow).getSeconds());
        var numFactory = series.numFactory();
        return seconds <= 0 ? numFactory.zero()
                : numFactory.numOf(seconds).dividedBy(numFactory.numOf(SECONDS_PER_YEAR));
    }

}
