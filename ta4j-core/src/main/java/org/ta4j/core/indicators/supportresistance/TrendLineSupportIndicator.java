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
package org.ta4j.core.indicators.supportresistance;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Projects a rising or falling support trend line that spans a configurable
 * look-back window of swing lows.
 * <p>
 * The indicator selects the straight line that touches the greatest number of
 * confirmed swing lows within the window. When multiple candidates touch the
 * same number of swing points, the line that sits beneath the current low price
 * is preferred, followed by the line that spans the widest distance between its
 * anchor swing lows.
 *
 * @see <a href="https://www.investopedia.com/trading/trendlines/">Investopedia:
 *      Trendlines</a>
 * @see <a href="https://www.investopedia.com/terms/s/support.asp">Investopedia:
 *      Support</a>
 * @since 0.20
 */
public class TrendLineSupportIndicator extends AbstractTrendLineIndicator {

    /**
     * Builds a support trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-low detector.
     *
     * @param priceIndicator      the indicator supplying the candidate swing-low
     *                            values
     * @param precedingHigherBars number of immediately preceding bars that must be
     *                            strictly higher than a swing low
     * @param followingHigherBars number of immediately following bars that must be
     *                            strictly higher than a swing low
     * @param allowedEqualBars    number of bars on each side that may equal the
     *                            swing-low value
     * @param barCount            number of bars to look back when selecting swing
     *                            points for the trend line
     * @since 0.20
     */
    public TrendLineSupportIndicator(Indicator<Num> priceIndicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars, int barCount, ScoringWeights scoringWeights) {
        this(new RecentFractalSwingLowIndicator(priceIndicator, precedingHigherBars, followingHigherBars,
                allowedEqualBars), precedingHigherBars, followingHigherBars, barCount, scoringWeights);
    }

    public TrendLineSupportIndicator(Indicator<Num> priceIndicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars, int barCount) {
        this(priceIndicator, precedingHigherBars, followingHigherBars, allowedEqualBars, barCount,
                ScoringWeights.defaultWeights());
    }

    /**
     * Builds a support trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-low detector.
     *
     * @param priceIndicator      the indicator supplying the candidate swing-low
     *                            values
     * @param precedingHigherBars number of immediately preceding bars that must be
     *                            strictly higher than a swing low
     * @param followingHigherBars number of immediately following bars that must be
     *                            strictly higher than a swing low
     * @param allowedEqualBars    number of bars on each side that may equal the
     *                            swing-low value
     * @since 0.20
     */
    public TrendLineSupportIndicator(Indicator<Num> priceIndicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars) {
        this(priceIndicator, precedingHigherBars, followingHigherBars, allowedEqualBars, Integer.MAX_VALUE,
                ScoringWeights.defaultWeights());
    }

    public TrendLineSupportIndicator(Indicator<Num> priceIndicator, int precedingHigherBars, int followingHigherBars,
            int allowedEqualBars, ScoringWeights scoringWeights) {
        this(priceIndicator, precedingHigherBars, followingHigherBars, allowedEqualBars, Integer.MAX_VALUE,
                scoringWeights);
    }

    /**
     * Builds a support trend line from a swing-low indicator implementation.
     *
     * @param recentSwingLowIndicator the swing-low indicator to use
     * @param precedingHigherBars     number of immediately preceding bars that must
     *                                be strictly higher than a swing low
     * @param followingHigherBars     number of immediately following bars that must
     *                                be strictly higher than a swing low
     * @param barCount                number of bars to look back when selecting
     *                                swing points for the trend line
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator recentSwingLowIndicator, int precedingHigherBars,
            int followingHigherBars, int barCount, ScoringWeights scoringWeights) {
        super(recentSwingLowIndicator, barCount, precedingHigherBars + followingHigherBars, TrendLineSide.SUPPORT,
                scoringWeights);
    }

    public TrendLineSupportIndicator(RecentSwingIndicator recentSwingLowIndicator, int precedingHigherBars,
            int followingHigherBars, int barCount) {
        this(recentSwingLowIndicator, precedingHigherBars, followingHigherBars, barCount,
                ScoringWeights.defaultWeights());
    }

    /**
     * Builds a support trend line from a swing-low indicator implementation.
     *
     * @param recentSwingLowIndicator the swing-low indicator to use
     * @param precedingHigherBars     number of immediately preceding bars that must
     *                                be strictly higher than a swing low
     * @param followingHigherBars     number of immediately following bars that must
     *                                be strictly higher than a swing low
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator recentSwingLowIndicator, int precedingHigherBars,
            int followingHigherBars) {
        this(recentSwingLowIndicator, precedingHigherBars, followingHigherBars, Integer.MAX_VALUE,
                ScoringWeights.defaultWeights());
    }

    public TrendLineSupportIndicator(RecentSwingIndicator recentSwingLowIndicator, int precedingHigherBars,
            int followingHigherBars, ScoringWeights scoringWeights) {
        this(recentSwingLowIndicator, precedingHigherBars, followingHigherBars, Integer.MAX_VALUE, scoringWeights);
    }

    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, int unstableBars,
            double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
            double recencyWeight) {
        this(swingLowIndicator, barCount, unstableBars, TrendLineSide.SUPPORT, touchWeight, extremeWeight,
                outsideWeight, proximityWeight, recencyWeight, ToleranceSettings.defaultSettings());
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weights.
     */
    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, int unstableBars,
            TrendLineSide side, double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
            double recencyWeight, ToleranceSettings toleranceSettings) {
        super(swingLowIndicator, barCount, unstableBars, side, touchWeight, extremeWeight, outsideWeight,
                proximityWeight, recencyWeight, toleranceSettings);
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weights
     * and tolerance parameters.
     */
    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, int unstableBars,
            double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
            double recencyWeight, String toleranceMode, double toleranceValue, double toleranceMinimum) {
        this(swingLowIndicator, barCount, unstableBars, TrendLineSide.SUPPORT, touchWeight, extremeWeight,
                outsideWeight, proximityWeight, recencyWeight,
                ToleranceSettings.from(toleranceMode, toleranceValue, toleranceMinimum));
    }

    /**
     * Deserialization-friendly constructor with numeric tolerance parameters to
     * simplify serialization.
     */
    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, int unstableBars,
            double touchWeight, double extremeWeight, double outsideWeight, double proximityWeight,
            double recencyWeight, double toleranceValue, double toleranceMinimum, int toleranceModeOrdinal) {
        this(swingLowIndicator, barCount, unstableBars, TrendLineSide.SUPPORT, touchWeight, extremeWeight,
                outsideWeight, proximityWeight, recencyWeight, ToleranceSettings
                        .from(ToleranceSettings.Mode.values()[toleranceModeOrdinal], toleranceValue, toleranceMinimum));
    }

    /**
     * Builds a support trend line by analysing the low price of each bar using a
     * symmetric look-back and look-forward window.
     *
     * @param series                the series to analyse
     * @param surroundingHigherBars number of bars on each side that must be
     *                              strictly higher than the swing low
     * @param barCount              number of bars to look back when selecting swing
     *                              points for the trend line
     * @since 0.20
     */
    public TrendLineSupportIndicator(BarSeries series, int surroundingHigherBars, int barCount,
            ScoringWeights scoringWeights) {
        this(new LowPriceIndicator(series), surroundingHigherBars, surroundingHigherBars, 0, barCount, scoringWeights);
    }

    public TrendLineSupportIndicator(BarSeries series, int surroundingHigherBars, int barCount) {
        this(series, surroundingHigherBars, barCount, ScoringWeights.defaultWeights());
    }

    /**
     * Builds a support trend line by analysing the low price of each bar using a
     * symmetric look-back and look-forward window.
     *
     * @param series                the series to analyse
     * @param surroundingHigherBars number of bars on each side that must be
     *                              strictly higher than the swing low
     * @since 0.20
     */
    public TrendLineSupportIndicator(BarSeries series, int surroundingHigherBars) {
        this(series, surroundingHigherBars, Integer.MAX_VALUE, ScoringWeights.defaultWeights());
    }

    /**
     * Builds a support trend line that uses a default three-bar symmetric window.
     *
     * @param series the series to analyse
     * @since 0.20
     */
    public TrendLineSupportIndicator(BarSeries series) {
        this(series, 3, Integer.MAX_VALUE, ScoringWeights.defaultWeights());
    }
}
