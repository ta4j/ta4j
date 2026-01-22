/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.temporal.ChronoUnit;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that measures the share of time spent in the market,
 * returned in the configured {@link ReturnRepresentation} format.
 *
 * <p>
 * The criterion compares the time covered by open positions to the overall
 * trading period and expresses it as a percentage. This helps users understand
 * capital utilization and exposure.
 *
 * <p>
 * <b>Return Representation:</b> This criterion defaults to
 * {@link ReturnRepresentation#DECIMAL} (ratios are typically expressed as
 * decimals), but you can override it via the constructor. The calculated ratio
 * (which represents the percentage of time in position) is converted to the
 * configured representation format.
 *
 * <p>
 * <b>Usage Examples:</b>
 *
 * <pre>{@code
 * // Default DECIMAL representation
 * var inPosition = new InPositionPercentageCriterion();
 * // Result: 0.5 means strategy is in position 50% of the time
 *
 * // PERCENTAGE representation
 * var inPositionPercentage = new InPositionPercentageCriterion(ReturnRepresentation.PERCENTAGE);
 * // Result: 50.0 means strategy is in position 50% of the time
 *
 * // MULTIPLICATIVE representation
 * var inPositionMultiplicative = new InPositionPercentageCriterion(ReturnRepresentation.MULTIPLICATIVE);
 * // Result: 1.5 means strategy is in position 50% of the time (1 + 0.5 = 1.5)
 * }</pre>
 *
 * <p>
 * <b>Ratio Format Examples:</b> A ratio of 0.5 (50% of time in position) can be
 * expressed as:
 * <ul>
 * <li><b>DECIMAL</b>: 0.5 (50% of time)
 * <li><b>PERCENTAGE</b>: 50.0 (50% of time)
 * <li><b>MULTIPLICATIVE</b>: 1.5 (1 + 0.5 = 1.5)
 * </ul>
 *
 * @see ReturnRepresentation
 * @see ReturnRepresentationPolicy
 * @since 0.19
 */
public class InPositionPercentageCriterion extends AbstractAnalysisCriterion {

    private final ReturnRepresentation returnRepresentation;

    /**
     * Constructor with {@link ReturnRepresentation#DECIMAL} as the default
     * (percentages are typically expressed as decimals).
     * <p>
     * The ratio output will be in DECIMAL format (e.g., 0.5 means 50% of time in
     * position). Use the other constructor to specify a different representation.
     */
    public InPositionPercentageCriterion() {
        this(ReturnRepresentation.DECIMAL);
    }

    /**
     * Constructor with explicit return representation.
     * <p>
     * Use this constructor to specify how the ratio output should be formatted. The
     * ratio represents the percentage of time the strategy is in position. See the
     * class javadoc for examples of how ratios are expressed in different formats.
     *
     * @param returnRepresentation the return representation to use for the output
     *                             ratio (e.g.,
     *                             {@link ReturnRepresentation#DECIMAL},
     *                             {@link ReturnRepresentation#PERCENTAGE},
     *                             {@link ReturnRepresentation#MULTIPLICATIVE})
     */
    public InPositionPercentageCriterion(ReturnRepresentation returnRepresentation) {
        this.returnRepresentation = returnRepresentation;
    }

    /**
     * Calculates how long a single position stays open relative to the entire
     * series duration.
     *
     * @param series   the bar series providing the trading period
     * @param position the position to evaluate
     * @return the percentage of the series duration covered by the position
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (series.isEmpty()) {
            return numFactory.zero();
        }
        var totalDuration = totalTradingDuration(series);
        if (totalDuration == 0) {
            return numFactory.zero();
        }
        var positionDuration = positionDuration(series, position);
        // Calculate the ratio as a rate of return (0-based)
        var ratio = numFactory.numOf(positionDuration).dividedBy(numFactory.numOf(totalDuration));
        // Convert the ratio to the configured representation
        return returnRepresentation.toRepresentationFromRateOfReturn(ratio);
    }

    /**
     * Calculates how long the strategy stays invested across all positions in the
     * trading record.
     *
     * @param series        the bar series providing the trading period
     * @param tradingRecord the trading record containing the positions to evaluate
     * @return the percentage of the series duration covered by the record's
     *         positions in the configured return representation format
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        if (series.isEmpty()) {
            return numFactory.zero();
        }
        var totalDuration = totalTradingDuration(series);
        if (totalDuration == 0 || tradingRecord.getPositionCount() == 0) {
            return numFactory.zero();
        }
        var positionDuration = tradingRecord.getPositions().stream().mapToLong(p -> positionDuration(series, p)).sum();
        // Calculate the ratio as a rate of return (0-based)
        var ratio = numFactory.numOf(positionDuration).dividedBy(numFactory.numOf(totalDuration));
        // Convert the ratio to the configured representation
        return returnRepresentation.toRepresentationFromRateOfReturn(ratio);
    }

    @Override
    public java.util.Optional<ReturnRepresentation> getReturnRepresentation() {
        return java.util.Optional.of(returnRepresentation);
    }

    /**
     * Indicates whether the first percentage is preferable to the second.
     *
     * @param criterionValue1 the first value to compare
     * @param criterionValue2 the second value to compare
     * @return {@code true} when the first value is lower (less time in the market)
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }

    private static long totalTradingDuration(BarSeries series) {
        var start = series.getFirstBar().getBeginTime();
        var end = series.getLastBar().getEndTime();
        return ChronoUnit.NANOS.between(start, end);
    }

    private static long positionDuration(BarSeries series, Position position) {
        if (position == null || position.isNew() || position.getEntry() == null) {
            return 0L;
        }
        var entryStart = series.getBar(position.getEntry().getIndex()).getBeginTime();
        var exitIndex = position.isClosed() ? position.getExit().getIndex() : series.getEndIndex();
        var exitEnd = series.getBar(exitIndex).getEndTime();
        return ChronoUnit.NANOS.between(entryStart, exitEnd);
    }
}
