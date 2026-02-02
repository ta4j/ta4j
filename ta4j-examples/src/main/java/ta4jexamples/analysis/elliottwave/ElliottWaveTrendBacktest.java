/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.time.format.DateTimeFormatter;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottSwingCompressor;
import org.ta4j.core.indicators.elliott.ElliottTrendBias;
import org.ta4j.core.indicators.elliott.ElliottTrendBiasIndicator;
import org.ta4j.core.indicators.elliott.ElliottWaveFacade;
import org.ta4j.core.num.Num;

import ta4jexamples.datasources.CsvFileBarSeriesDataSource;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Backtests and walk-forward tests Elliott Wave trend bias predictions using
 * ossified datasets.
 *
 * <p>
 * This demo evaluates whether the trend bias derived from Elliott Wave
 * scenarios correctly predicts the direction of price action over a fixed
 * lookahead horizon. It runs a full-history backtest and a rolling walk-forward
 * evaluation over multiple instruments.
 *
 * @since 0.22.2
 */
public class ElliottWaveTrendBacktest {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveTrendBacktest.class);

    private static final double DEFAULT_FIB_TOLERANCE = 0.25;
    private static final int DEFAULT_LOOKAHEAD_BARS = 20;
    private static final double DEFAULT_MIN_STRENGTH = 0.20;
    private static final int DEFAULT_WALK_FORWARD_WINDOW = 180;
    private static final int DEFAULT_WALK_FORWARD_STEP = 60;

    /**
     * Runs the trend-bias backtest and walk-forward evaluation demo.
     *
     * @param args command-line arguments (unused)
     */
    public static void main(String[] args) {
        List<DatasetSpec> datasets = List.of(
                DatasetSpec.json("Coinbase-ETH-USD-PT1D-20160517_20251028.json", "ETH-USD PT1D (Coinbase)",
                        ElliottDegree.PRIMARY),
                DatasetSpec.csv("AAPL-PT1D-20130102_20131231.csv", "AAPL PT1D (Yahoo)", ElliottDegree.MINOR));

        for (DatasetSpec dataset : datasets) {
            BarSeries series = dataset.loadSeries();
            if (series == null || series.isEmpty()) {
                LOG.warn("Dataset {} could not be loaded or is empty.", dataset.label());
                continue;
            }

            LOG.info("=== Elliott Trend Bias Backtest: {} ===", dataset.label());
            LOG.info("Bars: {} | Range: {}", series.getBarCount(), describeRange(series));

            ElliottWaveFacade facade = buildFacade(series, dataset.degree());
            ElliottTrendBiasIndicator trendBiasIndicator = facade.trendBias();

            TrendAccuracy backtest = evaluateWindow(series, trendBiasIndicator, series.getBeginIndex(),
                    series.getEndIndex(), DEFAULT_LOOKAHEAD_BARS, DEFAULT_MIN_STRENGTH);
            logAccuracy("Backtest", backtest, DEFAULT_LOOKAHEAD_BARS, DEFAULT_MIN_STRENGTH);

            runWalkForward(series, trendBiasIndicator, DEFAULT_LOOKAHEAD_BARS, DEFAULT_MIN_STRENGTH,
                    DEFAULT_WALK_FORWARD_WINDOW, DEFAULT_WALK_FORWARD_STEP);
        }
    }

    /**
     * Builds an Elliott Wave facade with zigzag swings and compression.
     *
     * @param series bar series to analyze
     * @param degree Elliott wave degree to use
     * @return configured Elliott Wave facade
     */
    private static ElliottWaveFacade buildFacade(BarSeries series, ElliottDegree degree) {
        ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
        return ElliottWaveFacade.zigZag(series, degree, Optional.of(series.numFactory().numOf(DEFAULT_FIB_TOLERANCE)),
                Optional.of(compressor));
    }

    /**
     * Evaluates directional accuracy across a contiguous bar window.
     *
     * @param series             bar series being evaluated
     * @param trendBiasIndicator indicator supplying trend bias values
     * @param startIndex         first index to evaluate
     * @param endIndex           last index to evaluate
     * @param lookaheadBars      bars to look ahead for validation
     * @param minStrength        minimum bias strength to include
     * @return aggregated accuracy statistics
     */
    private static TrendAccuracy evaluateWindow(BarSeries series, ElliottTrendBiasIndicator trendBiasIndicator,
            int startIndex, int endIndex, int lookaheadBars, double minStrength) {
        int effectiveStart = Math.max(startIndex, series.getBeginIndex());
        int effectiveEnd = Math.min(endIndex, series.getEndIndex());
        int unstableBars = trendBiasIndicator.getCountOfUnstableBars();
        effectiveStart = Math.max(effectiveStart, unstableBars);

        int lastEvaluationIndex = effectiveEnd - Math.max(1, lookaheadBars);
        if (lastEvaluationIndex < effectiveStart) {
            return TrendAccuracy.empty();
        }

        int totalPredictions = 0;
        int correctPredictions = 0;
        int bullishPredictions = 0;
        int bullishCorrect = 0;
        int bearishPredictions = 0;
        int bearishCorrect = 0;
        int skipped = 0;

        for (int i = effectiveStart; i <= lastEvaluationIndex; i++) {
            ElliottTrendBias bias = trendBiasIndicator.getValue(i);
            if (bias == null || bias.isUnknown() || bias.isNeutral()) {
                skipped++;
                continue;
            }

            double strength = bias.strength();
            if (Double.isNaN(strength) || strength < minStrength) {
                skipped++;
                continue;
            }

            Num currentClose = series.getBar(i).getClosePrice();
            Num futureClose = series.getBar(i + lookaheadBars).getClosePrice();
            if (Num.isNaNOrNull(currentClose) || Num.isNaNOrNull(futureClose)) {
                skipped++;
                continue;
            }

            boolean bullish = bias.isBullish();
            boolean correct = bullish ? futureClose.isGreaterThan(currentClose) : futureClose.isLessThan(currentClose);

            totalPredictions++;
            if (bullish) {
                bullishPredictions++;
                if (correct) {
                    bullishCorrect++;
                }
            } else {
                bearishPredictions++;
                if (correct) {
                    bearishCorrect++;
                }
            }
            if (correct) {
                correctPredictions++;
            }
        }

        return new TrendAccuracy(totalPredictions, correctPredictions, bullishPredictions, bullishCorrect,
                bearishPredictions, bearishCorrect, skipped);
    }

    /**
     * Runs a rolling walk-forward evaluation across the series.
     *
     * @param series             bar series being evaluated
     * @param trendBiasIndicator indicator supplying trend bias values
     * @param lookaheadBars      bars to look ahead for validation
     * @param minStrength        minimum bias strength to include
     * @param windowSize         rolling window size in bars
     * @param stepSize           step size between windows in bars
     */
    private static void runWalkForward(BarSeries series, ElliottTrendBiasIndicator trendBiasIndicator,
            int lookaheadBars, double minStrength, int windowSize, int stepSize) {
        int startIndex = Math.max(series.getBeginIndex(), trendBiasIndicator.getCountOfUnstableBars());
        int endIndex = series.getEndIndex();
        int availableBars = endIndex - startIndex + 1;
        if (availableBars <= 0) {
            LOG.info("Walk-forward skipped: insufficient bars after unstable period.");
            return;
        }

        int effectiveWindow = Math.min(windowSize, availableBars);
        int effectiveStep = Math.max(1, stepSize);

        LOG.info("Walk-forward windows: size={} bars, step={} bars", effectiveWindow, effectiveStep);

        for (int windowStart = startIndex; windowStart + effectiveWindow
                - 1 <= endIndex; windowStart += effectiveStep) {
            int windowEnd = windowStart + effectiveWindow - 1;
            TrendAccuracy accuracy = evaluateWindow(series, trendBiasIndicator, windowStart, windowEnd, lookaheadBars,
                    minStrength);
            String windowRange = describeRange(series, windowStart, windowEnd);
            LOG.info("Walk-forward {} -> {}", windowRange, formatAccuracy(accuracy, lookaheadBars, minStrength));
        }
    }

    /**
     * Logs summary accuracy output.
     *
     * @param label         label for the output
     * @param accuracy      aggregated accuracy statistics
     * @param lookaheadBars lookahead window used in bars
     * @param minStrength   minimum bias strength used for inclusion
     */
    private static void logAccuracy(String label, TrendAccuracy accuracy, int lookaheadBars, double minStrength) {
        LOG.info("{} -> {}", label, formatAccuracy(accuracy, lookaheadBars, minStrength));
    }

    /**
     * Formats accuracy statistics into a single summary string.
     *
     * @param accuracy      aggregated accuracy statistics
     * @param lookaheadBars lookahead window used in bars
     * @param minStrength   minimum bias strength used for inclusion
     * @return formatted summary string
     */
    private static String formatAccuracy(TrendAccuracy accuracy, int lookaheadBars, double minStrength) {
        String overall = formatPercent(accuracy.accuracy());
        String bullish = formatPercent(accuracy.bullishAccuracy());
        String bearish = formatPercent(accuracy.bearishAccuracy());
        return String.format(
                "lookahead=%d bars | minStrength=%.2f | predictions=%d | accuracy=%s | bullish=%s | bearish=%s | skipped=%d",
                lookaheadBars, minStrength, accuracy.totalPredictions(), overall, bullish, bearish,
                accuracy.skippedPredictions());
    }

    /**
     * Formats a ratio as a percentage string.
     *
     * @param value ratio value (0.0-1.0)
     * @return formatted percentage string, or {@code n/a} if undefined
     */
    private static String formatPercent(double value) {
        if (Double.isNaN(value)) {
            return "n/a";
        }
        return String.format("%.1f%%", value * 100.0);
    }

    /**
     * Describes the full range of the series in ISO date form.
     *
     * @param series bar series to describe
     * @return formatted date range string
     */
    private static String describeRange(BarSeries series) {
        return describeRange(series, series.getBeginIndex(), series.getEndIndex());
    }

    /**
     * Describes a subset of the series in ISO date form.
     *
     * @param series     bar series to describe
     * @param startIndex start bar index
     * @param endIndex   end bar index
     * @return formatted date range string
     */
    private static String describeRange(BarSeries series, int startIndex, int endIndex) {
        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE;
        String start = series.getBar(startIndex).getEndTime().atZone(ZoneOffset.UTC).toLocalDate().format(formatter);
        String end = series.getBar(endIndex).getEndTime().atZone(ZoneOffset.UTC).toLocalDate().format(formatter);
        return start + " -> " + end;
    }

    /**
     * Dataset metadata for backtesting inputs.
     *
     * @param resource   classpath resource identifier
     * @param label      label for logging output
     * @param degree     Elliott wave degree to analyze
     * @param sourceType data source kind
     */
    private record DatasetSpec(String resource, String label, ElliottDegree degree, DataSourceType sourceType) {

        /**
         * Loads the bar series for this dataset.
         *
         * @return loaded series, or {@code null} if unavailable
         */
        BarSeries loadSeries() {
            if (sourceType == DataSourceType.JSON) {
                return JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(resource);
            }
            return new CsvFileBarSeriesDataSource().loadSeries(resource);
        }

        /**
         * Builds a JSON-backed dataset spec.
         *
         * @param resource resource path
         * @param label    label for logging
         * @param degree   Elliott wave degree
         * @return dataset spec
         */
        static DatasetSpec json(String resource, String label, ElliottDegree degree) {
            return new DatasetSpec(resource, label, degree, DataSourceType.JSON);
        }

        /**
         * Builds a CSV-backed dataset spec.
         *
         * @param resource resource path
         * @param label    label for logging
         * @param degree   Elliott wave degree
         * @return dataset spec
         */
        static DatasetSpec csv(String resource, String label, ElliottDegree degree) {
            return new DatasetSpec(resource, label, degree, DataSourceType.CSV);
        }
    }

    private enum DataSourceType {
        JSON, CSV
    }

    /**
     * Aggregated trend-bias prediction accuracy metrics.
     *
     * @param totalPredictions   total evaluated predictions
     * @param correctPredictions total correct predictions
     * @param bullishPredictions total bullish predictions
     * @param bullishCorrect     correct bullish predictions
     * @param bearishPredictions total bearish predictions
     * @param bearishCorrect     correct bearish predictions
     * @param skippedPredictions skipped predictions
     */
    private record TrendAccuracy(int totalPredictions, int correctPredictions, int bullishPredictions,
            int bullishCorrect, int bearishPredictions, int bearishCorrect, int skippedPredictions) {

        /**
         * @return empty accuracy metrics
         */
        static TrendAccuracy empty() {
            return new TrendAccuracy(0, 0, 0, 0, 0, 0, 0);
        }

        /**
         * @return overall accuracy ratio
         */
        double accuracy() {
            return ratio(correctPredictions, totalPredictions);
        }

        /**
         * @return bullish-only accuracy ratio
         */
        double bullishAccuracy() {
            return ratio(bullishCorrect, bullishPredictions);
        }

        /**
         * @return bearish-only accuracy ratio
         */
        double bearishAccuracy() {
            return ratio(bearishCorrect, bearishPredictions);
        }

        /**
         * Computes a safe ratio, returning NaN when the denominator is zero.
         *
         * @param numerator   numerator value
         * @param denominator denominator value
         * @return ratio or NaN
         */
        private double ratio(int numerator, int denominator) {
            return denominator > 0 ? (double) numerator / denominator : Double.NaN;
        }
    }
}
