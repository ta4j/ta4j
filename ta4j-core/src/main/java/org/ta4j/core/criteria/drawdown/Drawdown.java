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
package org.ta4j.core.criteria.drawdown;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.CashFlow;
import org.ta4j.core.analysis.CumulativePnL;
import org.ta4j.core.num.Num;

/**
 * Utility for scanning drawdowns on an equity curve.
 * <p>
 * The supplied curve is typically a {@link CashFlow} or {@link CumulativePnL}.
 * The scan walks through the curve, tracking peak values and the maximum
 * decline that follows. Optionally the drawdown can be expressed relative to
 * the peak or in absolute terms.
 *
 * @since 0.19
 */
public final class Drawdown {

    private Drawdown() {
    }

    /**
     * Returns the maximum <i>relative</i> drawdown amount for the given equity
     * curve.
     *
     * @param series        the bar series
     * @param tradingRecord the trading record (optional, may be {@code null})
     * @param curve         the equity curve to scan
     * @return the maximum drawdown amount relative to the peak
     *
     * @since 0.19
     */
    public static Num amount(BarSeries series, TradingRecord tradingRecord, Indicator<Num> curve) {
        return amount(series, tradingRecord, curve, true);
    }

    /**
     * Returns the bar length between the peak and trough where the maximum
     * <i>relative</i> drawdown occurred.
     *
     * @param series        the bar series
     * @param tradingRecord the trading record (optional, may be {@code null})
     * @param curve         the equity curve to scan
     * @return the number of bars of the maximum drawdown
     *
     * @since 0.19
     */
    public static Num length(BarSeries series, TradingRecord tradingRecord, Indicator<Num> curve) {
        return length(series, tradingRecord, curve, true);
    }

    /**
     * Returns the maximum drawdown amount for the given equity curve.
     *
     * @param series        the bar series
     * @param tradingRecord the trading record (optional, may be {@code null})
     * @param curve         the equity curve to scan
     * @param relative      {@code true} to express drawdown relative to the peak,
     *                      {@code false} for absolute terms
     * @return the maximum drawdown amount
     *
     * @since 0.19
     */
    public static Num amount(BarSeries series, TradingRecord tradingRecord, Indicator<Num> curve, boolean relative) {
        return scan(series, tradingRecord, curve, relative).amount();
    }

    /**
     * Returns the bar length between the peak and trough where the maximum drawdown
     * occurred.
     *
     * @param series        the bar series
     * @param tradingRecord the trading record (optional, may be {@code null})
     * @param curve         the equity curve to scan
     * @param relative      {@code true} to express drawdown relative to the peak,
     *                      {@code false} for absolute terms
     * @return the number of bars of the maximum drawdown
     *
     * @since 0.19
     */
    public static Num length(BarSeries series, TradingRecord tradingRecord, Indicator<Num> curve, boolean relative) {
        var numFactory = series.numFactory();
        var drawdownLength = scan(series, tradingRecord, curve, relative).length();
        return numFactory.numOf(drawdownLength);
    }

    private record Scan(Num amount, int length) {
    }

    private static Scan scan(BarSeries series, TradingRecord tradingRecord, Indicator<Num> curve, boolean relative) {
        var numFactory = series.numFactory();
        var zero = numFactory.zero();
        var peak = zero;
        var peakIndex = series.getBeginIndex();
        var maxDrawdown = zero;
        var maxLength = 0;

        var begin = tradingRecord == null ? series.getBeginIndex() : tradingRecord.getStartIndex(series);
        var end = tradingRecord == null ? series.getEndIndex() : tradingRecord.getEndIndex(series);

        if (!series.isEmpty()) {
            for (var i = begin; i <= end; i++) {
                var value = curve.getValue(i);
                if (value.isGreaterThan(peak)) {
                    peak = value;
                    peakIndex = i;
                }
                var drop = relative ? peak.minus(value).dividedBy(peak) : peak.minus(value);
                if (drop.isGreaterThan(maxDrawdown)) {
                    maxDrawdown = drop;
                    maxLength = i - peakIndex;
                }
            }
        }

        return new Scan(maxDrawdown, maxLength);
    }

}