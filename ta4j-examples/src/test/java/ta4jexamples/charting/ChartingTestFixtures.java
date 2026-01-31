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
package ta4jexamples.charting;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.OHLCDataItem;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;

/**
 * Shared fixtures for charting tests. Relies on the {@code Mock*} utilities
 * from {@code ta4j-core} to guarantee deterministic datasets.
 */
public final class ChartingTestFixtures {

    private static final Instant START_TIME = Instant.EPOCH.plus(Duration.ofDays(1));
    private static final Duration DAILY_PERIOD = Duration.ofDays(1);
    private static final Duration HOURLY_PERIOD = Duration.ofHours(1);

    private ChartingTestFixtures() {
        throw new AssertionError("No instances");
    }

    public static BarSeries standardDailySeries() {
        return linearSeries("Test Series", DAILY_PERIOD, 10, 100.0, 1.0, 1.0, 1000.0, 100.0);
    }

    public static BarSeries dailySeries(final String name) {
        return linearSeries(name, DAILY_PERIOD, 10, 100.0, 1.0, 1.0, 1000.0, 100.0);
    }

    public static BarSeries hourlySeries(final String name) {
        return linearSeries(name, HOURLY_PERIOD, 5, 100.0, 0.5, 0.5, 500.0, 50.0);
    }

    public static BarSeries dailySeriesWithWeekendGap(final String name) {
        final var series = new MockBarSeriesBuilder().withName(name).build();
        addBar(series, START_TIME, DAILY_PERIOD, 100.0, 102.0, 99.0, 101.0, 1000.0);
        addBar(series, START_TIME.plus(Duration.ofDays(3)), DAILY_PERIOD, 101.0, 103.0, 100.0, 102.0, 1100.0);
        return series;
    }

    public static BarSeries problematicSeries() {
        final var series = new MockBarSeriesBuilder().withName("Problematic Series").build();
        addBar(series, START_TIME, DAILY_PERIOD, 0.0, 0.0, 0.0, 0.0, 0.0);
        return series;
    }

    public static BarSeries seriesWithSpecialChars() {
        final var series = new MockBarSeriesBuilder().withName("Test:Series/With\\Special?Chars*<>|\"").build();
        addBar(series, START_TIME, DAILY_PERIOD, 100.0, 105.0, 99.0, 104.0, 1000.0);
        return series;
    }

    public static TradingRecord completedTradeRecord(final BarSeries series) {
        final TradingRecord record = new BaseTradingRecord();

        if (series.getBarCount() >= 6) {
            final Trade buy = Trade.buyAt(2, series);
            final Trade sell = Trade.sellAt(5, series);

            record.enter(2, buy.getPricePerAsset(), buy.getAmount());
            record.exit(5, sell.getPricePerAsset(), sell.getAmount());
        }

        return record;
    }

    public static TradingRecord emptyRecord() {
        return new BaseTradingRecord();
    }

    public static TradingRecord openPositionRecord(final BarSeries series) {
        final TradingRecord record = new BaseTradingRecord();
        if (!series.isEmpty()) {
            final Bar firstBar = series.getBar(series.getBeginIndex());
            record.enter(0, firstBar.getClosePrice(), series.numFactory().numOf(1));
        }
        return record;
    }

    public static DefaultOHLCDataset singleCandleDataset(final boolean upCandle) {
        final double close = upCandle ? 104.0 : 96.0;
        return buildDataset("Test", List.of(ohlcItem(100.0, 105.0, 99.0, close, 1000.0, START_TIME)));
    }

    public static DefaultOHLCDataset candleDatasetWithZeros() {
        return buildDataset("Test", List.of(ohlcItem(0.0, 0.0, 0.0, 0.0, 0.0, START_TIME)));
    }

    public static DefaultOHLCDataset linearOhlcDataset(final String name, final int count) {
        final var items = IntStream.range(0, count).mapToObj(i -> {
            final Instant endTime = START_TIME.plus(DAILY_PERIOD.multipliedBy(i));
            final double open = 100.0 + i;
            final double high = open + 5.0;
            final double low = open - 1.0;
            final double close = open + 4.0;
            final double volume = 1000.0 + i * 100.0;
            return ohlcItem(open, high, low, close, volume, endTime);
        }).toList();
        return buildDataset(name, items);
    }

    public static DefaultOHLCDataset seriesToDataset(final BarSeries barSeries) {
        final var items = IntStream.rangeClosed(barSeries.getBeginIndex(), barSeries.getEndIndex()).mapToObj(i -> {
            final Bar bar = barSeries.getBar(i);
            return new OHLCDataItem(Date.from(bar.getEndTime()), bar.getOpenPrice().doubleValue(),
                    bar.getHighPrice().doubleValue(), bar.getLowPrice().doubleValue(),
                    bar.getClosePrice().doubleValue(), bar.getVolume().doubleValue());
        }).toArray(OHLCDataItem[]::new);

        final String datasetName = Objects.requireNonNullElse(barSeries.getName(), "Series");
        return new DefaultOHLCDataset(datasetName, items);
    }

    private static BarSeries linearSeries(final String name, final Duration period, final int count,
            final double baseOpen, final double openIncrement, final double closeOffset, final double baseVolume,
            final double volumeIncrement) {
        final var series = new MockBarSeriesBuilder().withName(name).build();
        for (int i = 0; i < count; i++) {
            final Instant endTime = START_TIME.plus(period.multipliedBy(i));
            final double open = baseOpen + i * openIncrement;
            final double high = open + 2.0;
            final double low = open - 1.0;
            final double close = open + closeOffset;
            final double volume = baseVolume + i * volumeIncrement;
            addBar(series, endTime, period, open, high, low, close, volume);
        }
        return series;
    }

    private static void addBar(final BarSeries series, final Instant endTime, final Duration period, final double open,
            final double high, final double low, final double close, final double volume) {
        series.barBuilder()
                .timePeriod(period)
                .endTime(endTime)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(volume)
                .add();
    }

    private static DefaultOHLCDataset buildDataset(final String name, final List<OHLCDataItem> items) {
        return new DefaultOHLCDataset(name, items.toArray(OHLCDataItem[]::new));
    }

    private static OHLCDataItem ohlcItem(final double open, final double high, final double low, final double close,
            final double volume, final Instant endTime) {
        return new OHLCDataItem(Date.from(endTime), open, high, low, close, volume);
    }
}
