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

import static org.ta4j.core.TestUtils.assertNumEquals;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.helpers.Statistic;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NumFactory;

public class PositionDurationCriterionTest extends AbstractCriterionTest {

    public PositionDurationCriterionTest(NumFactory numFactory) {
        super(params -> params.length == 0 ? new PositionDurationCriterion()
                : new PositionDurationCriterion((Statistic) params[0]), numFactory);
    }

    @Test
    public void calculateWithPosition() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110)
                .build();
        Position position = new Position(Trade.buyAt(1, series), Trade.sellAt(4, series));

        AnalysisCriterion criterion = getCriterion();
        long secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 3d, criterion.calculate(series, position));
    }

    @Test
    public void calculateWithNoPositions() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110)
                .build();

        AnalysisCriterion criterion = getCriterion();
        assertNumEquals(0, criterion.calculate(series, new BaseTradingRecord()));
    }

    @Test
    public void calculateWithMeanDurationByDefault() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series),
                Trade.buyAt(5, series), Trade.sellAt(15, series));

        AnalysisCriterion criterion = getCriterion();
        long secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();
        double expectedMean = secondsPerBar * (13.0 / 3.0);

        assertNumEquals(expectedMean, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithMedianDuration() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series),
                Trade.buyAt(5, series), Trade.sellAt(15, series));

        AnalysisCriterion criterion = getCriterion(Statistic.MEDIAN);
        long secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 2d, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithPercentileDuration() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series),
                Trade.buyAt(5, series), Trade.sellAt(15, series));

        AnalysisCriterion criterion = getCriterion(Statistic.P95);
        long secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 10d, criterion.calculate(series, tradingRecord));
    }

    @Test
    public void calculateWithMinimumDuration() {
        var series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 105, 110, 100, 95, 105, 110, 120, 125, 130, 135, 140, 145, 150, 155, 160)
                .build();
        TradingRecord tradingRecord = new BaseTradingRecord(
                Trade.buyAt(0, series), Trade.sellAt(1, series),
                Trade.buyAt(2, series), Trade.sellAt(4, series),
                Trade.buyAt(5, series), Trade.sellAt(15, series));

        AnalysisCriterion criterion = getCriterion(Statistic.MIN);
        long secondsPerBar = series.getBar(series.getBeginIndex()).getTimePeriod().toSeconds();

        assertNumEquals(secondsPerBar * 1d, criterion.calculate(series, tradingRecord));
    }
}
