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

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class InPositionPercentageCriterionTest extends AbstractCriterionTest {

    public InPositionPercentageCriterionTest(NumFactory numFactory) {
        super(params -> new InPositionPercentageCriterion(), numFactory);
    }

    @Test
    public void calculateReturnsPercentageForClosedPosition() {
        var series = buildSeries(5, Duration.ofHours(1));
        var amount = numFactory.one();
        var entry = Trade.buyAt(1, series.getBar(1).getClosePrice(), amount);
        var exit = Trade.sellAt(3, series.getBar(3).getClosePrice(), amount);
        var position = new Position(entry, exit);

        var criterion = getCriterion();
        var result = criterion.calculate(series, position);

        var totalDuration = totalDuration(series);
        var positionDuration = positionDuration(series, entry.getIndex(), exit.getIndex());
        var expectedPercentage = getExpectedPercentage(totalDuration, positionDuration);
        var expected = numFactory.numOf(expectedPercentage);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateReturnsPercentageForOpenPosition() {
        var series = buildSeries(5, Duration.ofHours(1));
        var amount = numFactory.one();
        var record = new BaseTradingRecord();
        record.enter(3, series.getBar(3).getClosePrice(), amount);
        var openPosition = record.getCurrentPosition();

        var criterion = getCriterion();
        var result = criterion.calculate(series, openPosition);

        var totalDuration = totalDuration(series);
        var positionDuration = positionDuration(series, openPosition.getEntry().getIndex(), series.getEndIndex());
        var expectedPercentage = getExpectedPercentage(totalDuration, positionDuration);
        var expected = numFactory.numOf(expectedPercentage);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateAggregatesDurationsAcrossRecord() {
        var series = buildSeries(5, Duration.ofHours(1));
        var record = new BaseTradingRecord();
        var amount = numFactory.one();

        record.enter(0, series.getBar(0).getClosePrice(), amount);
        record.exit(1, series.getBar(1).getClosePrice(), amount);

        record.enter(3, series.getBar(3).getClosePrice(), amount);
        record.exit(4, series.getBar(4).getClosePrice(), amount);

        var criterion = getCriterion();
        var result = criterion.calculate(series, record);

        var totalDuration = totalDuration(series);
        var accumulatedDuration = record.getPositions()
                .stream()
                .mapToLong(p -> positionDuration(series, p.getEntry().getIndex(),
                        p.isClosed() ? p.getExit().getIndex() : series.getEndIndex()))
                .sum();
        var expectedPercentage = getExpectedPercentage(totalDuration, accumulatedDuration);
        var expected = numFactory.numOf(expectedPercentage);

        assertNumEquals(expected, result);
    }

    @Test
    public void calculateReturnsZeroWhenRecordHasNoPositions() {
        var series = buildSeries(4, Duration.ofHours(1));
        var criterion = getCriterion();
        assertNumEquals(numFactory.zero(), criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateReturnsZeroWhenSeriesIsEmpty() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        var criterion = getCriterion();

        assertNumEquals(numFactory.zero(), criterion.calculate(series, new Position()));
        assertNumEquals(numFactory.zero(), criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void betterThanPrefersSmallerPercentage() {
        var criterion = getCriterion();
        assertTrue(criterion.betterThan(numFactory.numOf(20), numFactory.numOf(40)));
        assertFalse(criterion.betterThan(numFactory.numOf(60), numFactory.numOf(30)));
    }

    private BarSeries buildSeries(int barCount, Duration barDuration) {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        for (var i = 0; i < barCount; i++) {
            series.barBuilder()
                    .timePeriod(barDuration)
                    .closePrice(numFactory.zero())
                    .openPrice(numFactory.zero())
                    .highPrice(numFactory.zero())
                    .lowPrice(numFactory.zero())
                    .volume(numFactory.zero())
                    .trades(0)
                    .add();
        }
        return series;
    }

    private static long totalDuration(BarSeries series) {
        return ChronoUnit.NANOS.between(series.getFirstBar().getBeginTime(), series.getLastBar().getEndTime());
    }

    private static long positionDuration(BarSeries series, int entryIndex, int exitIndex) {
        var entryStart = series.getBar(entryIndex).getBeginTime();
        var exitEnd = series.getBar(exitIndex).getEndTime();
        return ChronoUnit.NANOS.between(entryStart, exitEnd);
    }

    private static double getExpectedPercentage(long totalDuration, double positionDuration) {
        return totalDuration == 0 ? 0 : positionDuration / totalDuration;
    }
}
