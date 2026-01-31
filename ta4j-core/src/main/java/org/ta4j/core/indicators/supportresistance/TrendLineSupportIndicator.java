/*
 * SPDX-License-Identifier: MIT
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
 * The indicator selects the straight line that maximizes a scoring function
 * across confirmed swing lows within the window. Scoring favors (in order of
 * weight) swing-touch count, extreme-anchor inclusion, minimizing outside
 * swings, proximity to swing prices, and recency of anchors. Tolerance for
 * touch proximity is configurable (percentage, absolute, or tick-size). The
 * currently selected segment can be retrieved via {@link #getCurrentSegment()}
 * for charting/diagnostics.
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
    /**
     * Builds a support trend line from an arbitrary indicator with custom scoring
     * weights.
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
     * @param scoringWeights      the scoring weights to use for candidate
     *                            evaluation
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

    /**
     * Builds a support trend line from an arbitrary indicator with custom scoring
     * weights, using all available bars in the series.
     *
     * @param priceIndicator      the indicator supplying the candidate swing-low
     *                            values
     * @param precedingHigherBars number of immediately preceding bars that must be
     *                            strictly higher than a swing low
     * @param followingHigherBars number of immediately following bars that must be
     *                            strictly higher than a swing low
     * @param allowedEqualBars    number of bars on each side that may equal the
     *                            swing-low value
     * @param scoringWeights      the scoring weights to use for candidate
     *                            evaluation
     * @since 0.20
     */
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
        super(recentSwingLowIndicator, barCount, TrendLineSide.SUPPORT, scoringWeights);
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

    /**
     * Builds a support trend line from a swing-low indicator with custom scoring
     * weights, using all available bars in the series.
     *
     * @param recentSwingLowIndicator the swing-low indicator to use
     * @param precedingHigherBars     number of immediately preceding bars that must
     *                                be strictly higher than a swing low
     * @param followingHigherBars     number of immediately following bars that must
     *                                be strictly higher than a swing low
     * @param scoringWeights          the scoring weights to use for candidate
     *                                evaluation
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator recentSwingLowIndicator, int precedingHigherBars,
            int followingHigherBars, ScoringWeights scoringWeights) {
        this(recentSwingLowIndicator, precedingHigherBars, followingHigherBars, Integer.MAX_VALUE, scoringWeights);
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weight
     * parameters as individual values.
     *
     * @param swingLowIndicator      the swing-low indicator to use
     * @param barCount               number of bars to look back when selecting
     *                               swing points
     * @param touchCountWeight       weight for swing point touch count
     * @param touchesExtremeWeight   weight for extreme point inclusion
     * @param outsideCountWeight     weight for minimizing outside swings
     * @param averageDeviationWeight weight for minimizing average deviation
     * @param anchorRecencyWeight    weight for anchor point recency
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, double touchCountWeight,
            double touchesExtremeWeight, double outsideCountWeight, double averageDeviationWeight,
            double anchorRecencyWeight) {
        super(swingLowIndicator, barCount, TrendLineSide.SUPPORT, touchCountWeight, touchesExtremeWeight,
                outsideCountWeight, averageDeviationWeight, anchorRecencyWeight, ToleranceSettings.defaultSettings(),
                DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE, DEFAULT_MAX_CANDIDATE_PAIRS);
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weights,
     * tolerance settings, and search caps.
     *
     * @param swingLowIndicator          the swing-low indicator to use
     * @param barCount                   number of bars to look back when selecting
     *                                   swing points
     * @param touchCountWeight           weight for swing point touch count
     * @param touchesExtremeWeight       weight for extreme point inclusion
     * @param outsideCountWeight         weight for minimizing outside swings
     * @param averageDeviationWeight     weight for minimizing average deviation
     * @param anchorRecencyWeight        weight for anchor point recency
     * @param toleranceSettings          tolerance settings for touch detection
     * @param maxSwingPointsForTrendline maximum number of swing points to consider
     * @param maxCandidatePairs          maximum number of candidate pairs to
     *                                   evaluate
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, double touchCountWeight,
            double touchesExtremeWeight, double outsideCountWeight, double averageDeviationWeight,
            double anchorRecencyWeight, ToleranceSettings toleranceSettings, int maxSwingPointsForTrendline,
            int maxCandidatePairs) {
        super(swingLowIndicator, barCount, TrendLineSide.SUPPORT, touchCountWeight, touchesExtremeWeight,
                outsideCountWeight, averageDeviationWeight, anchorRecencyWeight, toleranceSettings,
                maxSwingPointsForTrendline, maxCandidatePairs);
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weights
     * and tolerance parameters as strings/primitives.
     *
     * @param swingLowIndicator      the swing-low indicator to use
     * @param barCount               number of bars to look back when selecting
     *                               swing points
     * @param touchCountWeight       weight for swing point touch count
     * @param touchesExtremeWeight   weight for extreme point inclusion
     * @param outsideCountWeight     weight for minimizing outside swings
     * @param averageDeviationWeight weight for minimizing average deviation
     * @param anchorRecencyWeight    weight for anchor point recency
     * @param toleranceMode          tolerance mode as string (PERCENTAGE, ABSOLUTE,
     *                               or TICK_SIZE)
     * @param toleranceValue         tolerance value
     * @param toleranceMinimum       minimum absolute tolerance
     * @since 0.20
     */
    public TrendLineSupportIndicator(RecentSwingIndicator swingLowIndicator, int barCount, double touchCountWeight,
            double touchesExtremeWeight, double outsideCountWeight, double averageDeviationWeight,
            double anchorRecencyWeight, String toleranceMode, double toleranceValue, double toleranceMinimum) {
        super(swingLowIndicator, barCount, TrendLineSide.SUPPORT, touchCountWeight, touchesExtremeWeight,
                outsideCountWeight, averageDeviationWeight, anchorRecencyWeight,
                ToleranceSettings.from(toleranceMode, toleranceValue, toleranceMinimum),
                DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE, DEFAULT_MAX_CANDIDATE_PAIRS);
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
    /**
     * Builds a support trend line by analysing the low price of each bar using a
     * symmetric look-back and look-forward window with custom scoring weights.
     *
     * @param series                the series to analyse
     * @param surroundingHigherBars number of bars on each side that must be
     *                              strictly higher than the swing low
     * @param barCount              number of bars to look back when selecting swing
     *                              points for the trend line
     * @param scoringWeights        the scoring weights to use for candidate
     *                              evaluation
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
