package org.ta4j.core.criteria;

import java.time.temporal.ChronoUnit;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;

/**
 * Analysis criterion that measures the share of time spent in the market.
 *
 * <p>The criterion compares the time covered by open positions to the overall
 * trading period and expresses it as a percentage.</p>
 *
 * @since 0.19
 */
public class InPositionPercentageCriterion extends AbstractAnalysisCriterion {

    /**
     * Calculates how long a single position stays open relative to the entire
     * series duration.
     *
     * @param series the bar series providing the trading period
     * @param position the position to evaluate
     * @return the percentage of the series duration covered by the position
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = DecimalNumFactory.getInstance();
        var totalDuration = totalTradingDuration(series);
        if (series.isEmpty() || totalDuration == 0) {
            return numFactory.zero();
        }
        var positionDuration = positionDuration(series, position);
        var percentage = (double) positionDuration / totalDuration * 100;
        return numFactory.numOf(percentage);
    }

    /**
     * Calculates how long the strategy stays invested across all positions in the
     * trading record.
     *
     * @param series the bar series providing the trading period
     * @param tradingRecord the trading record containing the positions to
     *        evaluate
     * @return the percentage of the series duration covered by the record's
     *         positions
     * @since 0.19
     */
    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var numFactory = series.numFactory();
        var totalDuration = totalTradingDuration(series);
        if (series.isEmpty() || totalDuration == 0 || tradingRecord.getPositionCount() == 0) {
            return numFactory.zero();
        }
        var positionDuration = tradingRecord.getPositions().stream()
                .mapToLong(p -> positionDuration(series, p))
                .sum();
        var percentage = (double) positionDuration / totalDuration * 100;
        return numFactory.numOf(percentage);
    }

    /**
     * Indicates whether the first percentage is preferable to the second.
     *
     * @param criterionValue1 the first value to compare
     * @param criterionValue2 the second value to compare
     * @return {@code true} when the first value is lower (less time in the
     *         market)
     * @since 0.19
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
        var entryStart = series.getBar(position.getEntry().getIndex()).getBeginTime();
        var exitIndex = position.isClosed() ? position.getExit().getIndex() : series.getEndIndex();
        var exitEnd = series.getBar(exitIndex).getEndTime();
        return ChronoUnit.NANOS.between(entryStart, exitEnd);
    }
}

