/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import org.junit.Test;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import static org.ta4j.core.TestUtils.assertNumEquals;
import org.ta4j.core.Trade;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class PositionDurationCriterionTest extends AbstractCriterionTest {

    public PositionDurationCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 0 ? new PositionDurationCriterion()
                : new PositionDurationCriterion((Statistics) params[0]), numFactory);
    }

    @Test
    public void calculateWithPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110)
                .build();
        var position = new Position(Trade.buyAt(1, series), Trade.sellAt(4, series));

        var criterion = getCriterion();
        long secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 3d, criterion.calculate(series, position));
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(100, 105, 110).build();

        var criterion = getCriterion();
        assertNumEquals(0, criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithMeanDurationByDefault() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(15, series));

        var criterion = getCriterion();
        var secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();
        var expectedMean = secondsPerBar * (13.0 / 3.0);

        assertNumEquals(expectedMean, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithMedianDuration() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(15, series));

        var criterion = getCriterion(Statistics.MEDIAN);
        var secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 2d, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithPercentileDuration() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(15, series));

        var criterion = getCriterion(Statistics.P95);
        var secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 10d, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithMinimumDuration() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        var tradingRecord = new BaseTradingRecord(Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series), Trade.buyAt(5, series), Trade.sellAt(15, series));

        var criterion = getCriterion(Statistics.MIN);
        var secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 1d, criterion.calculate(series, tradingRecord));
    }
}
