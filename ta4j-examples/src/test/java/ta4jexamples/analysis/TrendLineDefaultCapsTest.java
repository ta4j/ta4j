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
package ta4jexamples.analysis;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.loaders.AdaptiveJsonBarsSerializer;
import ta4jexamples.loaders.CsvBarsLoader;
import ta4jexamples.loaders.CsvTradesLoader;

class TrendLineDefaultCapsTest {

    private record Dataset(String name, BarSeries series) {
    }

    private record SwingStats(int maxSwings, int maxPairs) {
    }

    private enum PriceSide {
        SUPPORT, RESISTANCE
    }

    @Test
    void defaultCapsCoverExampleDatasets() {
        final List<Dataset> datasets = List.of(
                new Dataset("appleinc_bars_from_20130101_usd.csv",
                        CsvBarsLoader.loadSeriesFromFile("appleinc_bars_from_20130101_usd.csv")),
                new Dataset("bitstamp_trades_from_20131125_usd.csv", CsvTradesLoader.loadBitstampSeries()),
                new Dataset("Binance-ETH-USD-PT5M-2023-3-13_2023-3-15.json",
                        loadJsonSeries("Binance-ETH-USD-PT5M-2023-3-13_2023-3-15.json")),
                new Dataset("Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json",
                        loadJsonSeries("Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json")),
                new Dataset("Coinbase-ETHUSD-Daily-2016-2025.json",
                        loadJsonSeries("Coinbase-ETHUSD-Daily-2016-2025.json")));

        int observedMaxSwings = 0;
        int observedMaxPairs = 0;

        for (Dataset dataset : datasets) {
            final BarSeries series = dataset.series();
            assertFalse(series.isEmpty(), () -> "Series " + dataset.name() + " should not be empty");
            final int trendLineLookback = Math.min(series.getBarCount(), 200);

            final SwingStats supportFractal = analyzeSwings(series,
                    new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 5, 5, 0), PriceSide.SUPPORT,
                    trendLineLookback);
            final SwingStats resistanceFractal = analyzeSwings(series,
                    new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 5, 5, 0), PriceSide.RESISTANCE,
                    trendLineLookback);
            final SwingStats supportZigZag = analyzeSwings(series, new RecentZigZagSwingLowIndicator(series),
                    PriceSide.SUPPORT, trendLineLookback);
            final SwingStats resistanceZigZag = analyzeSwings(series, new RecentZigZagSwingHighIndicator(series),
                    PriceSide.RESISTANCE, trendLineLookback);

            observedMaxSwings = maxOf(observedMaxSwings, supportFractal, resistanceFractal, supportZigZag,
                    resistanceZigZag);
            observedMaxPairs = maxPairs(observedMaxPairs, supportFractal, resistanceFractal, supportZigZag,
                    resistanceZigZag);
        }

        // Observed maxima across the bundled datasets (fractal and ZigZag, 200-bar
        // lookback): 18 swings and 153 candidate pairs.

        assertTrue(observedMaxSwings <= AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE,
                "Observed swings should fit within default cap");
        assertTrue(observedMaxPairs <= AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS,
                "Observed candidate pairs should fit within default cap");
    }

    private SwingStats analyzeSwings(BarSeries series, RecentSwingIndicator swingIndicator, PriceSide side,
            int lookback) {
        final Indicator<Num> priceIndicator = swingIndicator.getPriceIndicator();
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        int maxSwings = 0;
        for (int end = beginIndex; end <= endIndex; end++) {
            swingIndicator.getValue(end);
            final int windowStart = Math.max(beginIndex, end - lookback + 1);
            int swingCount = 0;
            for (int swingIndex : swingIndicator.getSwingPointIndexesUpTo(end)) {
                if (swingIndex < windowStart || swingIndex > end) {
                    continue;
                }
                if (isValidPrice(priceIndicator.getValue(swingIndex))) {
                    swingCount++;
                    continue;
                }
                if (isValidBarFallback(series, swingIndex, side)) {
                    swingCount++;
                }
            }
            if (swingCount > maxSwings) {
                maxSwings = swingCount;
            }
        }
        final int maxPairs = maxSwings < 2 ? 0 : (maxSwings * (maxSwings - 1)) / 2;
        return new SwingStats(maxSwings, maxPairs);
    }

    private boolean isValidPrice(Num value) {
        return value != null && !value.isNaN() && !Double.isNaN(value.doubleValue());
    }

    private boolean isValidBarFallback(BarSeries series, int index, PriceSide side) {
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return false;
        }
        final Num fallback = side == PriceSide.SUPPORT ? series.getBar(index).getLowPrice()
                : series.getBar(index).getHighPrice();
        return isValidPrice(fallback);
    }

    private int maxOf(int seed, SwingStats... stats) {
        int max = seed;
        for (SwingStats stat : stats) {
            max = Math.max(max, stat.maxSwings());
        }
        return max;
    }

    private int maxPairs(int seed, SwingStats... stats) {
        int max = seed;
        for (SwingStats stat : stats) {
            max = Math.max(max, stat.maxPairs());
        }
        return max;
    }

    private BarSeries loadJsonSeries(String resourceName) {
        final InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
        return AdaptiveJsonBarsSerializer.loadSeries(stream);
    }
}
