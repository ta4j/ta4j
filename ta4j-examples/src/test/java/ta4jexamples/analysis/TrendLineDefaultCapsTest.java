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
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
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

/**
 * Validates that the default capacity limits for trendline indicators are
 * sufficient to handle real-world market data from the bundled example
 * datasets.
 * <p>
 * Trendline indicators (e.g.,
 * {@link org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator}
 * and
 * {@link org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator})
 * use default caps to limit computational complexity:
 * <ul>
 * <li>{@value org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator#DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE}
 * swing points maximum</li>
 * <li>{@value org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator#DEFAULT_MAX_CANDIDATE_PAIRS}
 * candidate pairs maximum</li>
 * </ul>
 * <p>
 * This test exercises multiple swing point detection methods (fractal and
 * ZigZag) across diverse market datasets (stocks, crypto, different timeframes)
 * to ensure the default caps accommodate typical usage patterns. If this test
 * fails, it indicates that the default caps may need adjustment for broader
 * market coverage.
 * <p>
 * The test analyzes swing points within a 200-bar lookback window, which
 * represents a typical analysis window for trendline construction.
 *
 * @see org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator
 * @see org.ta4j.core.indicators.RecentFractalSwingHighIndicator
 * @see org.ta4j.core.indicators.RecentFractalSwingLowIndicator
 * @see org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator
 * @see org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator
 */
class TrendLineDefaultCapsTest {

    private static final Logger LOG = LogManager.getLogger(TrendLineDefaultCapsTest.class);

    /**
     * Represents a test dataset with its name and bar series.
     */
    private record Dataset(String name, BarSeries series) {
    }

    /**
     * Statistics about swing points observed in a dataset.
     *
     * @param maxSwings the maximum number of swing points found in any lookback
     *                  window
     * @param maxPairs  the maximum number of candidate pairs (n*(n-1)/2 for n
     *                  swings)
     */
    private record SwingStats(int maxSwings, int maxPairs) {
    }

    /**
     * Indicates whether we're analyzing support (lows) or resistance (highs)
     * trendlines.
     */
    private enum PriceSide {
        SUPPORT, RESISTANCE
    }

    /**
     * Validates that default capacity limits are sufficient for all bundled example
     * datasets.
     * <p>
     * This test:
     * <ol>
     * <li>Loads multiple real-world market datasets (stocks, crypto, various
     * timeframes)</li>
     * <li>Analyzes swing points using both fractal and ZigZag detection
     * methods</li>
     * <li>Tests both support (lows) and resistance (highs) trendlines</li>
     * <li>Tracks the maximum swings and candidate pairs observed across all
     * datasets</li>
     * <li>Asserts that observed maxima fit within the default capacity limits</li>
     * </ol>
     * <p>
     * If this test fails, it suggests that one or more datasets produce more swing
     * points than the default caps can handle, which may require:
     * <ul>
     * <li>Increasing the default caps in
     * {@link org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator}</li>
     * <li>Using extended constructors with higher capacity limits for those
     * specific datasets</li>
     * <li>Reducing the lookback window size for trendline analysis</li>
     * </ul>
     */
    @Test
    void defaultCapsCoverExampleDatasets() {
        // Load diverse market datasets covering different asset types and timeframes
        // Load datasets safely, skipping any that fail to load
        final List<Dataset> datasets = new ArrayList<>();
        addDatasetIfAvailable(datasets, "appleinc_bars_from_20130101_usd.csv",
                () -> CsvBarsLoader.loadSeriesFromFile("appleinc_bars_from_20130101_usd.csv"));
        addDatasetIfAvailable(datasets, "bitstamp_trades_from_20131125_usd.csv",
                () -> CsvTradesLoader.loadBitstampSeries());
        addDatasetIfAvailable(datasets, "Binance-ETH-USD-PT5M-2023-3-13_2023-3-15.json",
                () -> loadJsonSeries("Binance-ETH-USD-PT5M-2023-3-13_2023-3-15.json"));
        addDatasetIfAvailable(datasets, "Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json",
                () -> loadJsonSeries("Coinbase-ETH-USD-PT1D-2024-11-06_2025-10-21.json"));
        addDatasetIfAvailable(datasets, "Coinbase-ETHUSD-Daily-2016-2025.json",
                () -> loadJsonSeries("Coinbase-ETHUSD-Daily-2016-2025.json"));

        // Ensure at least one dataset was loaded successfully
        assertFalse(datasets.isEmpty(), "At least one dataset must be available for the test to be meaningful. "
                + "Check that dataset files are present in the test resources.");

        // Track the maximum swings and pairs observed across all datasets and methods
        int observedMaxSwings = 0;
        int observedMaxPairs = 0;

        for (Dataset dataset : datasets) {
            final BarSeries series = dataset.series();
            // Skip empty series (should not happen if addDatasetIfAvailable worked
            // correctly,
            // but double-check for safety)
            if (series == null || series.isEmpty()) {
                LOG.warn("Skipping empty series: {}", dataset.name());
                continue;
            }
            // Use a 200-bar lookback window (typical for trendline analysis)
            final int trendLineLookback = Math.min(series.getBarCount(), 200);

            // Analyze support trendlines using fractal swing detection (5-bar left/right
            // pattern)
            final SwingStats supportFractal = analyzeSwings(series,
                    new RecentFractalSwingLowIndicator(new LowPriceIndicator(series), 5, 5, 0), PriceSide.SUPPORT,
                    trendLineLookback);
            // Analyze resistance trendlines using fractal swing detection
            final SwingStats resistanceFractal = analyzeSwings(series,
                    new RecentFractalSwingHighIndicator(new HighPriceIndicator(series), 5, 5, 0), PriceSide.RESISTANCE,
                    trendLineLookback);
            // Analyze support trendlines using ZigZag swing detection
            final SwingStats supportZigZag = analyzeSwings(series, new RecentZigZagSwingLowIndicator(series),
                    PriceSide.SUPPORT, trendLineLookback);
            // Analyze resistance trendlines using ZigZag swing detection
            final SwingStats resistanceZigZag = analyzeSwings(series, new RecentZigZagSwingHighIndicator(series),
                    PriceSide.RESISTANCE, trendLineLookback);

            // Update global maxima across all swing detection methods
            observedMaxSwings = maxOf(observedMaxSwings, supportFractal, resistanceFractal, supportZigZag,
                    resistanceZigZag);
            observedMaxPairs = maxPairs(observedMaxPairs, supportFractal, resistanceFractal, supportZigZag,
                    resistanceZigZag);
        }

        // Observed maxima across the bundled datasets (fractal and ZigZag, 200-bar
        // lookback): 18 swings and 153 candidate pairs.
        // These values should be well below the default caps (64 swings, 2048 pairs).

        assertTrue(observedMaxSwings <= AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE,
                "Observed swings should fit within default cap");
        assertTrue(observedMaxPairs <= AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS,
                "Observed candidate pairs should fit within default cap");
    }

    /**
     * Analyzes swing points across all possible lookback windows to find the
     * maximum number of swings and candidate pairs that could be encountered.
     * <p>
     * This method simulates what a trendline indicator would encounter by:
     * <ol>
     * <li>Iterating through every possible end index in the series</li>
     * <li>For each end index, computing a lookback window</li>
     * <li>Counting valid swing points within that window</li>
     * <li>Tracking the maximum swing count observed</li>
     * <li>Computing the maximum candidate pairs (n*(n-1)/2 for n swings)</li>
     * </ol>
     * <p>
     * This ensures we test the worst-case scenario where the maximum number of
     * swings appears in a single lookback window.
     *
     * @param series         the bar series to analyze
     * @param swingIndicator the swing point detection indicator (fractal or ZigZag)
     * @param side           whether analyzing support (lows) or resistance (highs)
     * @param lookback       the lookback window size in bars
     * @return statistics about the maximum swings and pairs observed
     */
    private SwingStats analyzeSwings(BarSeries series, RecentSwingIndicator swingIndicator, PriceSide side,
            int lookback) {
        final Indicator<Num> priceIndicator = swingIndicator.getPriceIndicator();
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();
        int maxSwings = 0;
        // Test every possible end index to find the worst-case swing count
        for (int end = beginIndex; end <= endIndex; end++) {
            // Ensure the indicator is evaluated up to this point
            swingIndicator.getValue(end);
            // Calculate the start of the lookback window (sliding window)
            final int windowStart = Math.max(beginIndex, end - lookback + 1);
            int swingCount = 0;
            // Count valid swing points within the current lookback window
            for (int swingIndex : swingIndicator.getSwingPointIndexesUpTo(end)) {
                // Skip swings outside the current lookback window
                if (swingIndex < windowStart || swingIndex > end) {
                    continue;
                }
                // Try to get price from the swing indicator first
                if (isValidPrice(priceIndicator.getValue(swingIndex))) {
                    swingCount++;
                    continue;
                }
                // Fallback to bar high/low if indicator price is invalid
                if (isValidBarFallback(series, swingIndex, side)) {
                    swingCount++;
                }
            }
            // Track the maximum swing count across all windows
            if (swingCount > maxSwings) {
                maxSwings = swingCount;
            }
        }
        // Calculate maximum candidate pairs: n swings can form n*(n-1)/2 pairs
        final int maxPairs = maxSwings < 2 ? 0 : (maxSwings * (maxSwings - 1)) / 2;
        return new SwingStats(maxSwings, maxPairs);
    }

    /**
     * Validates that a price value is non-null and not NaN.
     *
     * @param value the price value to validate
     * @return true if the value is valid for trendline calculation
     */
    private boolean isValidPrice(Num value) {
        return value != null && !value.isNaN() && !Double.isNaN(value.doubleValue());
    }

    /**
     * Fallback method to extract a valid price from the bar series when the swing
     * indicator's price value is invalid.
     * <p>
     * Uses the bar's low price for support trendlines and high price for resistance
     * trendlines.
     *
     * @param series the bar series
     * @param index  the bar index
     * @param side   whether we need support (low) or resistance (high) price
     * @return true if a valid fallback price exists
     */
    private boolean isValidBarFallback(BarSeries series, int index, PriceSide side) {
        if (series == null || index < series.getBeginIndex() || index > series.getEndIndex()) {
            return false;
        }
        final Num fallback = side == PriceSide.SUPPORT ? series.getBar(index).getLowPrice()
                : series.getBar(index).getHighPrice();
        return isValidPrice(fallback);
    }

    /**
     * Finds the maximum swing count across multiple statistics.
     *
     * @param seed  the initial maximum value
     * @param stats variable number of swing statistics to compare
     * @return the maximum swing count found
     */
    private int maxOf(int seed, SwingStats... stats) {
        int max = seed;
        for (SwingStats stat : stats) {
            max = Math.max(max, stat.maxSwings());
        }
        return max;
    }

    /**
     * Finds the maximum candidate pair count across multiple statistics.
     *
     * @param seed  the initial maximum value
     * @param stats variable number of swing statistics to compare
     * @return the maximum candidate pair count found
     */
    private int maxPairs(int seed, SwingStats... stats) {
        int max = seed;
        for (SwingStats stat : stats) {
            max = Math.max(max, stat.maxPairs());
        }
        return max;
    }

    /**
     * Attempts to load a dataset and add it to the list if successful. If loading
     * fails or the series is empty/null, logs a warning and continues.
     *
     * @param datasets    the list to add the dataset to if loading succeeds
     * @param datasetName the name of the dataset (for logging)
     * @param loader      a supplier that loads the bar series
     */
    private void addDatasetIfAvailable(List<Dataset> datasets, String datasetName,
            java.util.function.Supplier<BarSeries> loader) {
        try {
            final BarSeries series = loader.get();
            if (series == null || series.isEmpty()) {
                LOG.warn("Dataset '{}' not found or is empty, skipping", datasetName);
                return;
            }
            datasets.add(new Dataset(datasetName, series));
        } catch (Exception e) {
            LOG.warn("Failed to load dataset '{}', skipping: {}", datasetName, e.getMessage());
        }
    }

    /**
     * Loads a JSON-formatted bar series from the classpath resources.
     *
     * @param resourceName the name of the resource file
     * @return the loaded bar series, or null if the resource is not found or an
     *         error occurs
     */
    private BarSeries loadJsonSeries(String resourceName) {
        final InputStream stream = getClass().getClassLoader().getResourceAsStream(resourceName);
        return AdaptiveJsonBarsSerializer.loadSeries(stream);
    }
}
