/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.supportresistance;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.num.Num;

/**
 * Projects a resistance trend line that spans a configurable look-back window
 * of swing highs.
 * <p>
 * The indicator selects the straight line that maximizes a scoring function
 * across confirmed swing highs within the window. Scoring favors (in order of
 * weight) swing-touch count, extreme-anchor inclusion, minimizing outside
 * swings, proximity to swing prices, and recency of anchors. Tolerance for
 * touch proximity is configurable (percentage, absolute, or tick-size). The
 * currently selected segment can be retrieved via {@link #getCurrentSegment()}
 * for charting/diagnostics.
 *
 * @see <a href="https://www.investopedia.com/trading/trendlines/">Investopedia:
 *      Trendlines</a>
 * @see <a href=
 *      "https://www.investopedia.com/terms/r/resistance.asp">Investopedia:
 *      Resistance</a>
 * @since 0.20
 */
public class TrendLineResistanceIndicator extends AbstractTrendLineIndicator {

    /**
     * Builds a resistance trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-high detector.
     *
     * @param indicator          the indicator supplying the candidate swing-high
     *                           values
     * @param precedingLowerBars number of immediately preceding bars that must be
     *                           strictly lower than a swing high
     * @param followingLowerBars number of immediately following bars that must be
     *                           strictly lower than a swing high
     * @param allowedEqualBars   number of bars on each side that may equal the
     *                           swing-high value
     * @param barCount           number of bars to look back when selecting swing
     *                           points for the trend line
     * @since 0.20
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars, int barCount) {
        this(indicator, precedingLowerBars, followingLowerBars, allowedEqualBars, barCount,
                ScoringWeights.defaultWeights());
    }

    /**
     * Builds a resistance trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-high detector.
     *
     * @param indicator          the indicator supplying the candidate swing-high
     *                           values
     * @param precedingLowerBars number of immediately preceding bars that must be
     *                           strictly lower than a swing high
     * @param followingLowerBars number of immediately following bars that must be
     *                           strictly lower than a swing high
     * @param allowedEqualBars   number of bars on each side that may equal the
     *                           swing-high value
     * @param barCount           number of bars to look back when selecting swing
     *                           points for the trend line
     * @since 0.20
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars, int barCount, ScoringWeights scoringWeights) {
        this(new RecentFractalSwingHighIndicator(indicator, precedingLowerBars, followingLowerBars, allowedEqualBars),
                precedingLowerBars, followingLowerBars, barCount, scoringWeights);
    }

    /**
     * Builds a resistance trend line from an arbitrary indicator that provides the
     * values inspected by the underlying swing-high detector.
     *
     * @param indicator          the indicator supplying the candidate swing-high
     *                           values
     * @param precedingLowerBars number of immediately preceding bars that must be
     *                           strictly lower than a swing high
     * @param followingLowerBars number of immediately following bars that must be
     *                           strictly lower than a swing high
     * @param allowedEqualBars   number of bars on each side that may equal the
     *                           swing-high value
     * @since 0.20
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars) {
        this(indicator, precedingLowerBars, followingLowerBars, allowedEqualBars, Integer.MAX_VALUE,
                ScoringWeights.defaultWeights());
    }

    /**
     * Builds a resistance trend line from an arbitrary indicator with custom
     * scoring weights, using all available bars in the series.
     *
     * @param indicator          the indicator supplying the candidate swing-high
     *                           values
     * @param precedingLowerBars number of immediately preceding bars that must be
     *                           strictly lower than a swing high
     * @param followingLowerBars number of immediately following bars that must be
     *                           strictly lower than a swing high
     * @param allowedEqualBars   number of bars on each side that may equal the
     *                           swing-high value
     * @param scoringWeights     the scoring weights to use for candidate evaluation
     * @since 0.20
     */
    public TrendLineResistanceIndicator(Indicator<Num> indicator, int precedingLowerBars, int followingLowerBars,
            int allowedEqualBars, ScoringWeights scoringWeights) {
        this(indicator, precedingLowerBars, followingLowerBars, allowedEqualBars, Integer.MAX_VALUE, scoringWeights);
    }

    /**
     * Builds a resistance trend line from a swing-high indicator implementation.
     *
     * @param recentSwingHighIndicator the swing-high indicator to use
     * @param precedingLowerBars       number of immediately preceding bars that
     *                                 must be strictly lower than a swing high
     * @param followingLowerBars       number of immediately following bars that
     *                                 must be strictly lower than a swing high
     * @param barCount                 number of bars to look back when selecting
     *                                 swing points for the trend line
     * @since 0.20
     */
    public TrendLineResistanceIndicator(RecentSwingIndicator recentSwingHighIndicator, int precedingLowerBars,
            int followingLowerBars, int barCount, ScoringWeights scoringWeights) {
        super(recentSwingHighIndicator, barCount, TrendLineSide.RESISTANCE, scoringWeights);
    }

    /**
     * Constructs a TrendLineResistanceIndicator with the specified swing high
     * indicator and bar parameters.
     *
     * @param recentSwingHighIndicator the indicator that identifies recent swing
     *                                 high points
     * @param precedingLowerBars       the number of bars that must be lower before
     *                                 a swing high to confirm the trend line
     * @param followingLowerBars       the number of bars that must be lower after a
     *                                 swing high to confirm the trend line
     * @param barCount                 the total number of bars to consider when
     *                                 calculating the trend line resistance
     *
     * @since 0.20
     */
    public TrendLineResistanceIndicator(RecentSwingIndicator recentSwingHighIndicator, int precedingLowerBars,
            int followingLowerBars, int barCount) {
        this(recentSwingHighIndicator, precedingLowerBars, followingLowerBars, barCount,
                ScoringWeights.defaultWeights());
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weight
     * parameters as individual values.
     *
     * @param swingHighIndicator     the swing-high indicator to use
     * @param barCount               number of bars to look back when selecting
     *                               swing points
     * @param touchCountWeight       weight for swing point touch count
     * @param touchesExtremeWeight   weight for extreme point inclusion
     * @param outsideCountWeight     weight for minimizing outside swings
     * @param averageDeviationWeight weight for minimizing average deviation
     * @param anchorRecencyWeight    weight for anchor point recency
     * @since 0.20
     */
    public TrendLineResistanceIndicator(RecentSwingIndicator swingHighIndicator, int barCount, double touchCountWeight,
            double touchesExtremeWeight, double outsideCountWeight, double averageDeviationWeight,
            double anchorRecencyWeight) {
        super(swingHighIndicator, barCount, TrendLineSide.RESISTANCE, touchCountWeight, touchesExtremeWeight,
                outsideCountWeight, averageDeviationWeight, anchorRecencyWeight, ToleranceSettings.defaultSettings(),
                DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE, DEFAULT_MAX_CANDIDATE_PAIRS);
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weights,
     * tolerance settings, and search caps.
     *
     * @param swingHighIndicator         the swing-high indicator to use
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
    public TrendLineResistanceIndicator(RecentSwingIndicator swingHighIndicator, int barCount, double touchCountWeight,
            double touchesExtremeWeight, double outsideCountWeight, double averageDeviationWeight,
            double anchorRecencyWeight, ToleranceSettings toleranceSettings, int maxSwingPointsForTrendline,
            int maxCandidatePairs) {
        super(swingHighIndicator, barCount, TrendLineSide.RESISTANCE, touchCountWeight, touchesExtremeWeight,
                outsideCountWeight, averageDeviationWeight, anchorRecencyWeight, toleranceSettings,
                maxSwingPointsForTrendline, maxCandidatePairs);
    }

    /**
     * Deserialization-friendly constructor that accepts explicit scoring weights
     * and tolerance parameters as strings/primitives.
     *
     * @param swingHighIndicator     the swing-high indicator to use
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
    public TrendLineResistanceIndicator(RecentSwingIndicator swingHighIndicator, int barCount, double touchCountWeight,
            double touchesExtremeWeight, double outsideCountWeight, double averageDeviationWeight,
            double anchorRecencyWeight, String toleranceMode, double toleranceValue, double toleranceMinimum) {
        super(swingHighIndicator, barCount, TrendLineSide.RESISTANCE, touchCountWeight, touchesExtremeWeight,
                outsideCountWeight, averageDeviationWeight, anchorRecencyWeight,
                ToleranceSettings.from(toleranceMode, toleranceValue, toleranceMinimum),
                DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE, DEFAULT_MAX_CANDIDATE_PAIRS);
    }

    /**
     * Builds a resistance trend line from a swing-high indicator implementation.
     *
     * @param recentSwingHighIndicator the swing-high indicator to use
     * @param precedingLowerBars       number of immediately preceding bars that
     *                                 must be strictly lower than a swing high
     * @param followingLowerBars       number of immediately following bars that
     *                                 must be strictly lower than a swing high
     * @since 0.20
     */
    public TrendLineResistanceIndicator(RecentSwingIndicator recentSwingHighIndicator, int precedingLowerBars,
            int followingLowerBars) {
        this(recentSwingHighIndicator, precedingLowerBars, followingLowerBars, Integer.MAX_VALUE);
    }

    /**
     * Builds a resistance trend line by analysing the high price of each bar using
     * a symmetric look-back and look-forward window.
     *
     * @param series               the series to analyse
     * @param surroundingLowerBars number of bars on each side that must be strictly
     *                             lower than the swing high
     * @param barCount             number of bars to look back when selecting swing
     *                             points for the trend line
     * @since 0.20
     */
    /**
     * Builds a resistance trend line by analysing the high price of each bar using
     * a symmetric look-back and look-forward window with custom scoring weights.
     *
     * @param series               the series to analyse
     * @param surroundingLowerBars number of bars on each side that must be strictly
     *                             lower than the swing high
     * @param barCount             number of bars to look back when selecting swing
     *                             points for the trend line
     * @param scoringWeights       the scoring weights to use for candidate
     *                             evaluation
     * @since 0.20
     */
    public TrendLineResistanceIndicator(BarSeries series, int surroundingLowerBars, int barCount,
            ScoringWeights scoringWeights) {
        this(new HighPriceIndicator(series), surroundingLowerBars, surroundingLowerBars, 0, barCount, scoringWeights);
    }

    public TrendLineResistanceIndicator(BarSeries series, int surroundingLowerBars, int barCount) {
        this(series, surroundingLowerBars, barCount, ScoringWeights.defaultWeights());
    }

    /**
     * Builds a resistance trend line by analysing the high price of each bar using
     * a symmetric look-back and look-forward window.
     *
     * @param series               the series to analyse
     * @param surroundingLowerBars number of bars on each side that must be strictly
     *                             lower than the swing high
     * @since 0.20
     */
    public TrendLineResistanceIndicator(BarSeries series, int surroundingLowerBars) {
        this(series, surroundingLowerBars, Integer.MAX_VALUE, ScoringWeights.defaultWeights());
    }

    /**
     * Builds a resistance trend line that uses a default three-bar symmetric
     * window.
     *
     * @param series the series to analyse
     * @since 0.20
     */
    public TrendLineResistanceIndicator(BarSeries series) {
        this(series, 3, Integer.MAX_VALUE, ScoringWeights.defaultWeights());
    }
}
