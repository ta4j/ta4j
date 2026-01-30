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
