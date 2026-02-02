/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

/**
 * Position duration criterion.
 *
 * <p>
 * Returns the summary statistic of position durations.
 *
 * @since 0.22.2
 */
public class PositionDurationCriterion extends AbstractAnalysisCriterion {

    private final Statistics statistics;

    /**
     * Default constructor using the mean duration.
     *
     * @since 0.22.2
     */
    public PositionDurationCriterion() {
        this(Statistics.MEAN);
    }

    /**
     * Constructor.
     *
     * @param statistics statistic to return
     * @since 0.22.2
     */
    public PositionDurationCriterion(Statistics statistics) {
        this.statistics = statistics;
    }

    @Override
    public Num calculate(BarSeries series, Position position) {
        var numFactory = series.numFactory();
        if (position == null || !position.isClosed()) {
            return numFactory.zero();
        }
        var exitIndex = position.getExit().getIndex();
        var entryIndex = position.getEntry().getIndex();
        var indexDuration = exitIndex - entryIndex;
        var secondsPeriod = series.getFirstBar().getTimePeriod().toSeconds();
        return numFactory.numOf(indexDuration * secondsPeriod);
    }

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord) {
        var durations = tradingRecord.getPositions()
                .stream()
                .filter(Position::isClosed)
                .map(position -> calculate(series, position))
                .toArray(Num[]::new);
        return statistics.calculate(series.numFactory(), durations);
    }

    /**
     * The lower the criterion value, the better.
     */
    @Override
    public boolean betterThan(Num criterionValue1, Num criterionValue2) {
        return criterionValue1.isLessThan(criterionValue2);
    }
}
