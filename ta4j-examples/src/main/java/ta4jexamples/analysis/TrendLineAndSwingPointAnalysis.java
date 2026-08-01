/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis;

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.RecentFractalSwingHighIndicator;
import org.ta4j.core.indicators.RecentFractalSwingLowIndicator;
import org.ta4j.core.indicators.RecentSwingIndicator;
import org.ta4j.core.indicators.RecentSwingIndicators;
import org.ta4j.core.indicators.RecentSwingIndicators.Method;
import org.ta4j.core.indicators.RecentSwingIndicators.Pair;
import org.ta4j.core.indicators.SwingPointMarkerIndicator;
import org.ta4j.core.indicators.helpers.HighPriceIndicator;
import org.ta4j.core.indicators.helpers.LowPriceIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator;
import org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator.TrendLineSegment;
import org.ta4j.core.indicators.supportresistance.TrendLineResistanceIndicator;
import org.ta4j.core.indicators.supportresistance.TrendLineSupportIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator;
import org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.utils.BarSeriesUtils;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BitStampCsvTradesFileBarSeriesDataSource;
import ta4jexamples.datasources.CsvFileBarSeriesDataSource;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;

/**
 * Combined regression + visualization harness for trendline and swing point
 * indicators.
 *
 * <p>
 * This runner performs three kinds of verification:
 * <ul>
 * <li>Capacity headroom checks across bundled example datasets (fractal +
 * ZigZag, highs + lows) to ensure default caps remain comfortably above
 * real-world usage.</li>
 * <li>Recent Coinbase ETH/USD comparison across five-minute, fifteen-minute,
 * hourly, four-hour, and daily bars for every paired swing methodology.</li>
 * <li>Chart generation that overlays trendline indicators and swing point
 * markers on a representative dataset, enabling quick visual inspection after
 * large changes.</li>
 * </ul>
 *
 * <p>
 * The companion unit test asserts the regression invariants programmatically
 * and validates that rendered overlays contain data without requiring GUI
 * display.
 *
 * @see org.ta4j.core.indicators.supportresistance.AbstractTrendLineIndicator
 * @see org.ta4j.core.indicators.RecentFractalSwingHighIndicator
 * @see org.ta4j.core.indicators.RecentFractalSwingLowIndicator
 * @see org.ta4j.core.indicators.RecentSwingIndicators
 * @see org.ta4j.core.indicators.zigzag.RecentZigZagSwingHighIndicator
 * @see org.ta4j.core.indicators.zigzag.RecentZigZagSwingLowIndicator
 */
public class TrendLineAndSwingPointAnalysis {

    static final int DEFAULT_TRENDLINE_LOOKBACK = 200;
    static final int DEFAULT_SURROUNDING_BARS = 5;

    static final int DEFAULT_FRACTAL_PRECEDING_BARS = 5;
    static final int DEFAULT_FRACTAL_FOLLOWING_BARS = 5;
    static final int DEFAULT_FRACTAL_ALLOWED_EQUAL_BARS = 0;

    static final int HEADROOM_SWING_POINT_LIMIT = AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE / 2;
    static final int HEADROOM_CANDIDATE_PAIR_LIMIT = AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS / 2;

    static final String DEFAULT_CHART_OUTPUT_DIRECTORY = "temp/charts";
    static final String DEFAULT_CHART_FILE_NAME = "trendline-swingpoint-analysis";
    static final String RECENT_ETH_FIVE_MINUTE_RESOURCE = "Coinbase-ETH-USD-PT5M-20260721_20260723.json";
    static final String RECENT_ETH_HOURLY_RESOURCE = "Coinbase-ETH-USD-PT1H-20260718_20260723.json";
    static final String RECENT_ETH_FOUR_HOUR_RESOURCE = "Coinbase-ETH-USD-PT4H-20260401_20260723.json";
    static final String RECENT_ETH_DAILY_RESOURCE = "Coinbase-ETH-USD-PT1D-20260101_20260723.json";

    private static final Logger LOG = LogManager.getLogger(TrendLineAndSwingPointAnalysis.class);

    /**
     * Represents a dataset with its name and bar series.
     */
    record Dataset(String name, BarSeries series) {
    }

    private record DatasetSpec(String name, Supplier<BarSeries> loader) {
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

    private record HeadroomObservation(String datasetName, Method method, PriceSide side, int lookback,
            SwingStats stats) {
    }

    /**
     * Indicates whether we're analyzing support (lows) or resistance (highs)
     * trendlines.
     */
    private enum PriceSide {
        SUPPORT, RESISTANCE
    }

    record RecentEthSeries(Duration duration, BarSeries series) {
    }

    record SwingObservation(Duration duration, Method method, int highCount, int lowCount, int latestHighIndex,
            int latestLowIndex, int latestHighConfirmationIndex, int latestLowConfirmationIndex) {
    }

    public static void main(String[] args) {
        Config config = Config.fromArgs(args);
        TrendLineAndSwingPointAnalysis analysis = new TrendLineAndSwingPointAnalysis();
        analysis.verifyDefaultCapsHeadroomForBundledDatasets();
        analysis.analyzeRecentEthSwings();

        BarSeries chartSeries = analysis.loadChartSeries(config.chartDatasetResource());
        AnalysisChartArtifacts artifacts = analysis.buildAnalysisChartArtifacts(chartSeries,
                Math.min(chartSeries.getBarCount(), config.trendLineLookback()), config.surroundingBars());
        analysis.renderAnalysisChart(artifacts, config);
    }

    List<SwingObservation> analyzeRecentEthSwings() {
        final List<SwingObservation> observations = new ArrayList<>();
        for (RecentEthSeries data : loadRecentEthSeries()) {
            final int endIndex = data.series().getEndIndex();
            for (Pair pair : buildSwingMethods(data.series())) {
                final List<Integer> highs = pair.highs().getSwingPointIndexesUpTo(endIndex);
                final List<Integer> lows = pair.lows().getSwingPointIndexesUpTo(endIndex);
                final SwingObservation observation = new SwingObservation(data.duration(), pair.method(), highs.size(),
                        lows.size(), pair.highs().getLatestSwingIndex(endIndex),
                        pair.lows().getLatestSwingIndex(endIndex),
                        pair.highs().getLatestSwingConfirmationIndex(endIndex),
                        pair.lows().getLatestSwingConfirmationIndex(endIndex));
                observations.add(observation);
                LOG.info(
                        "ETH/USD swing summary: duration={}, method={}, highs={}, lows={}, latestHigh={}, latestLow={}, highConfirmedAt={}, lowConfirmedAt={}",
                        observation.duration(), observation.method(), observation.highCount(), observation.lowCount(),
                        observation.latestHighIndex(), observation.latestLowIndex(),
                        observation.latestHighConfirmationIndex(), observation.latestLowConfirmationIndex());
            }
        }
        return List.copyOf(observations);
    }

    List<RecentEthSeries> loadRecentEthSeries() {
        final BarSeries fiveMinute = loadRequiredJsonSeries(RECENT_ETH_FIVE_MINUTE_RESOURCE);
        final BarSeries hourly = loadRequiredJsonSeries(RECENT_ETH_HOURLY_RESOURCE);
        final BarSeries fourHour = loadRequiredJsonSeries(RECENT_ETH_FOUR_HOUR_RESOURCE);
        final BarSeries daily = loadRequiredJsonSeries(RECENT_ETH_DAILY_RESOURCE);
        return List.of(new RecentEthSeries(Duration.ofMinutes(5), fiveMinute),
                new RecentEthSeries(Duration.ofMinutes(15),
                        BarSeriesUtils.aggregateBars(fiveMinute, Duration.ofMinutes(15), "ETH-USD-PT15M")),
                new RecentEthSeries(Duration.ofHours(1), hourly), new RecentEthSeries(Duration.ofHours(4), fourHour),
                new RecentEthSeries(Duration.ofDays(1), daily));
    }

    List<Pair> buildSwingMethods(BarSeries series) {
        return List.of(RecentSwingIndicators.defaultFor(series), RecentSwingIndicators.fractal(series),
                RecentSwingIndicators.adaptiveZigZag(series), RecentSwingIndicators.slopeChange(series),
                RecentSwingIndicators.prominence(series), RecentSwingIndicators.consensus(series));
    }

    void verifyDefaultCapsHeadroomForBundledDatasets() {
        List<Dataset> datasets = loadRequiredDatasets();
        List<HeadroomObservation> observations = new ArrayList<>();

        for (Dataset dataset : datasets) {
            BarSeries series = dataset.series();
            int lookback = Math.min(series.getBarCount(), DEFAULT_TRENDLINE_LOOKBACK);
            observations.addAll(analyzeHeadroom(dataset, lookback));
        }

        verifyHeadroom(observations);
        logHeadroomSummary(observations, datasets.size());
    }

    private List<HeadroomObservation> analyzeHeadroom(Dataset dataset, int lookback) {
        BarSeries series = dataset.series();

        LowPriceIndicator lowPrice = new LowPriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);

        RecentSwingIndicator fractalLows = new RecentFractalSwingLowIndicator(lowPrice, DEFAULT_FRACTAL_PRECEDING_BARS,
                DEFAULT_FRACTAL_FOLLOWING_BARS, DEFAULT_FRACTAL_ALLOWED_EQUAL_BARS);
        RecentSwingIndicator fractalHighs = new RecentFractalSwingHighIndicator(highPrice,
                DEFAULT_FRACTAL_PRECEDING_BARS, DEFAULT_FRACTAL_FOLLOWING_BARS, DEFAULT_FRACTAL_ALLOWED_EQUAL_BARS);

        RecentSwingIndicator zigzagLows = new RecentZigZagSwingLowIndicator(series);
        RecentSwingIndicator zigzagHighs = new RecentZigZagSwingHighIndicator(series);

        return List.of(
                new HeadroomObservation(dataset.name(), Method.FRACTAL, PriceSide.SUPPORT, lookback,
                        analyzeSwings(series, fractalLows, PriceSide.SUPPORT, lookback)),
                new HeadroomObservation(dataset.name(), Method.FRACTAL, PriceSide.RESISTANCE, lookback,
                        analyzeSwings(series, fractalHighs, PriceSide.RESISTANCE, lookback)),
                new HeadroomObservation(dataset.name(), Method.ZIGZAG, PriceSide.SUPPORT, lookback,
                        analyzeSwings(series, zigzagLows, PriceSide.SUPPORT, lookback)),
                new HeadroomObservation(dataset.name(), Method.ZIGZAG, PriceSide.RESISTANCE, lookback,
                        analyzeSwings(series, zigzagHighs, PriceSide.RESISTANCE, lookback)));
    }

    private SwingStats analyzeSwings(BarSeries series, RecentSwingIndicator swingIndicator, PriceSide side,
            int lookback) {
        final Indicator<Num> priceIndicator = swingIndicator.getPriceIndicator();
        final int beginIndex = series.getBeginIndex();
        final int endIndex = series.getEndIndex();

        swingIndicator.getValue(endIndex);
        validateSwingIndexes(series, swingIndicator.getSwingPointIndexesUpTo(endIndex));

        int maxSwings = 0;
        for (int end = beginIndex; end <= endIndex; end++) {
            swingIndicator.getValue(end);
            final int windowStart = Math.max(beginIndex, end - lookback + 1);
            int swingCount = 0;
            List<Integer> swingPoints = swingIndicator.getSwingPointIndexesUpTo(end);
            for (int i = swingPoints.size() - 1; i >= 0; i--) {
                int swingIndex = swingPoints.get(i);
                if (swingIndex < windowStart) {
                    break;
                }
                if (isValidPrice(priceIndicator.getValue(swingIndex)) || isValidBarFallback(series, swingIndex, side)) {
                    swingCount++;
                }
            }
            maxSwings = Math.max(maxSwings, swingCount);
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
        Bar bar = series.getBar(index);
        final Num fallback = side == PriceSide.SUPPORT ? bar.getLowPrice() : bar.getHighPrice();
        return isValidPrice(fallback);
    }

    private void validateSwingIndexes(BarSeries series, List<Integer> swingIndexes) {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();

        Integer previous = null;
        for (Integer index : swingIndexes) {
            requireTrue(index != null, "Swing indicator returned a null index");
            requireTrue(index >= beginIndex && index <= endIndex,
                    "Swing index " + index + " must fall within [" + beginIndex + ", " + endIndex + "]");
            if (previous != null) {
                requireTrue(index > previous,
                        "Swing indexes must be strictly increasing (found " + previous + " then " + index + ")");
            }
            previous = index;
        }
    }

    private void verifyHeadroom(List<HeadroomObservation> observations) {
        for (HeadroomObservation observation : observations) {
            SwingStats stats = observation.stats();

            requireTrue(stats.maxSwings() <= AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE,
                    headroomMessage(observation, "Exceeded default swing cap", stats.maxSwings(),
                            AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE));
            requireTrue(stats.maxPairs() <= AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS,
                    headroomMessage(observation, "Exceeded default candidate-pair cap", stats.maxPairs(),
                            AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS));

            requireTrue(stats.maxSwings() <= HEADROOM_SWING_POINT_LIMIT, headroomMessage(observation,
                    "Swing headroom shrank below 50%", stats.maxSwings(), HEADROOM_SWING_POINT_LIMIT));
            requireTrue(stats.maxPairs() <= HEADROOM_CANDIDATE_PAIR_LIMIT, headroomMessage(observation,
                    "Candidate-pair headroom shrank below 50%", stats.maxPairs(), HEADROOM_CANDIDATE_PAIR_LIMIT));
        }
    }

    private String headroomMessage(HeadroomObservation observation, String prefix, int observed, int limit) {
        return prefix + " for dataset=" + observation.datasetName + ", method=" + observation.method + ", side="
                + observation.side + ", lookback=" + observation.lookback + " (observed=" + observed + ", limit="
                + limit + ")";
    }

    private void logHeadroomSummary(List<HeadroomObservation> observations, int datasetCount) {
        int maxSwings = 0;
        int maxPairs = 0;
        for (HeadroomObservation observation : observations) {
            SwingStats stats = observation.stats();
            maxSwings = Math.max(maxSwings, stats.maxSwings());
            maxPairs = Math.max(maxPairs, stats.maxPairs());
        }

        LOG.info(
                "Trendline cap headroom summary: datasets={}, maxSwings={}, maxPairs={}, caps(swings={}, pairs={}), headroom(swings<= {}, pairs<= {})",
                datasetCount, maxSwings, maxPairs, AbstractTrendLineIndicator.DEFAULT_MAX_SWING_POINTS_FOR_TRENDLINE,
                AbstractTrendLineIndicator.DEFAULT_MAX_CANDIDATE_PAIRS, HEADROOM_SWING_POINT_LIMIT,
                HEADROOM_CANDIDATE_PAIR_LIMIT);
    }

    private BarSeries loadJsonSeries(String resourceName) {
        final InputStream stream = TrendLineAndSwingPointAnalysis.class.getClassLoader()
                .getResourceAsStream(resourceName);
        if (stream == null) {
            return null;
        }
        return JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
    }

    private BarSeries loadRequiredJsonSeries(String resourceName) {
        final BarSeries series = loadJsonSeries(resourceName);
        requireTrue(series != null && !series.isEmpty(), "Required dataset '" + resourceName + "' is missing or empty");
        return series;
    }

    private List<Dataset> loadRequiredDatasets() {
        List<Dataset> datasets = new ArrayList<>();
        for (DatasetSpec spec : requiredDatasetSpecs()) {
            datasets.add(loadRequiredDataset(spec));
        }
        return datasets;
    }

    private List<DatasetSpec> requiredDatasetSpecs() {
        return List.of(
                new DatasetSpec("AAPL-PT1D-20130102_20131231.csv",
                        () -> CsvFileBarSeriesDataSource.loadSeriesFromFile("AAPL-PT1D-20130102_20131231.csv")),
                new DatasetSpec("Bitstamp-BTC-USD-PT5M-20131125_20131201.csv",
                        () -> BitStampCsvTradesFileBarSeriesDataSource
                                .loadBitstampSeries("Bitstamp-BTC-USD-PT5M-20131125_20131201.csv")),
                new DatasetSpec("Binance-ETH-USD-PT5M-20230313_20230315.json",
                        () -> loadJsonSeries("Binance-ETH-USD-PT5M-20230313_20230315.json")),
                new DatasetSpec("Coinbase-ETH-USD-PT1D-20241105_20251020.json",
                        () -> loadJsonSeries("Coinbase-ETH-USD-PT1D-20241105_20251020.json")),
                new DatasetSpec("Coinbase-ETH-USD-PT1D-20160517_20251028.json",
                        () -> loadJsonSeries("Coinbase-ETH-USD-PT1D-20160517_20251028.json")));
    }

    private Dataset loadRequiredDataset(DatasetSpec spec) {
        BarSeries series;
        try {
            series = spec.loader.get();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load required dataset '" + spec.name + '\'', e);
        }
        requireTrue(series != null && !series.isEmpty(), "Required dataset '" + spec.name + "' is missing or empty");
        return new Dataset(spec.name, series);
    }

    private BarSeries loadChartSeries(String datasetResource) {
        if (datasetResource == null || datasetResource.isBlank()) {
            BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();
            requireTrue(series != null && !series.isEmpty(), "Default chart series is missing or empty");
            return series;
        }

        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile(datasetResource);
        if (series == null || series.isEmpty()) {
            series = loadJsonSeries(datasetResource);
        }
        requireTrue(series != null && !series.isEmpty(), "Chart series '" + datasetResource + "' is missing or empty");
        return series;
    }

    record TrendLineVariants(TrendLineSupportIndicator support, TrendLineSupportIndicator supportTouchBiased,
            TrendLineSupportIndicator supportExtremeBiased, TrendLineResistanceIndicator resistance,
            TrendLineResistanceIndicator resistanceTouchBiased, TrendLineResistanceIndicator resistanceExtremeBiased) {
    }

    record SwingMarkerVariants(SwingPointMarkerIndicator fractalLows, SwingPointMarkerIndicator fractalHighs,
            SwingPointMarkerIndicator zigzagLows, SwingPointMarkerIndicator zigzagHighs) {
    }

    record AnalysisChartArtifacts(BarSeries series, TrendLineVariants trendLines, SwingMarkerVariants swings,
            ChartPlan plan) {
    }

    AnalysisChartArtifacts buildAnalysisChartArtifacts(BarSeries series, int lookback, int surroundingBars) {
        Objects.requireNonNull(series, "Series cannot be null");
        requireFalse(series.isEmpty(), "Series cannot be empty");

        TrendLineVariants trendLines = buildTrendLines(series, lookback, surroundingBars);
        SwingMarkerVariants swings = buildSwingMarkers(series);
        ChartPlan plan = buildChartPlan(series, trendLines, swings);
        return new AnalysisChartArtifacts(series, trendLines, swings, plan);
    }

    private TrendLineVariants buildTrendLines(BarSeries series, int lookback, int surroundingBars) {
        TrendLineSupportIndicator support = new TrendLineSupportIndicator(series, surroundingBars, lookback);
        TrendLineSupportIndicator supportTouchBiased = new TrendLineSupportIndicator(series, surroundingBars, lookback,
                TrendLineSupportIndicator.ScoringWeights.touchCountBiasPreset());
        TrendLineSupportIndicator supportExtremeBiased = new TrendLineSupportIndicator(series, surroundingBars,
                lookback, TrendLineSupportIndicator.ScoringWeights.extremeSwingBiasPreset());

        TrendLineResistanceIndicator resistance = new TrendLineResistanceIndicator(series, surroundingBars, lookback);
        TrendLineResistanceIndicator resistanceTouchBiased = new TrendLineResistanceIndicator(series, surroundingBars,
                lookback, TrendLineResistanceIndicator.ScoringWeights.touchCountBiasPreset());
        TrendLineResistanceIndicator resistanceExtremeBiased = new TrendLineResistanceIndicator(series, surroundingBars,
                lookback, TrendLineResistanceIndicator.ScoringWeights.extremeSwingBiasPreset());

        return new TrendLineVariants(support, supportTouchBiased, supportExtremeBiased, resistance,
                resistanceTouchBiased, resistanceExtremeBiased);
    }

    private SwingMarkerVariants buildSwingMarkers(BarSeries series) {
        LowPriceIndicator lowPrice = new LowPriceIndicator(series);
        HighPriceIndicator highPrice = new HighPriceIndicator(series);

        RecentFractalSwingLowIndicator fractalLows = new RecentFractalSwingLowIndicator(lowPrice,
                DEFAULT_FRACTAL_PRECEDING_BARS, DEFAULT_FRACTAL_FOLLOWING_BARS, DEFAULT_FRACTAL_ALLOWED_EQUAL_BARS);
        RecentFractalSwingHighIndicator fractalHighs = new RecentFractalSwingHighIndicator(highPrice,
                DEFAULT_FRACTAL_PRECEDING_BARS, DEFAULT_FRACTAL_FOLLOWING_BARS, DEFAULT_FRACTAL_ALLOWED_EQUAL_BARS);

        RecentZigZagSwingLowIndicator zigzagLows = new RecentZigZagSwingLowIndicator(series);
        RecentZigZagSwingHighIndicator zigzagHighs = new RecentZigZagSwingHighIndicator(series);

        SwingPointMarkerIndicator fractalLowMarkers = new SwingPointMarkerIndicator(series, fractalLows);
        SwingPointMarkerIndicator fractalHighMarkers = new SwingPointMarkerIndicator(series, fractalHighs);
        SwingPointMarkerIndicator zigzagLowMarkers = new SwingPointMarkerIndicator(series, zigzagLows);
        SwingPointMarkerIndicator zigzagHighMarkers = new SwingPointMarkerIndicator(series, zigzagHighs);

        return new SwingMarkerVariants(fractalLowMarkers, fractalHighMarkers, zigzagLowMarkers, zigzagHighMarkers);
    }

    private ChartPlan buildChartPlan(BarSeries series, TrendLineVariants trendLines, SwingMarkerVariants swings) {
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        return chartWorkflow.builder()
                .withTitle("Trendlines + Swing Points (Regression Harness)")
                .withSeries(series)
                .withIndicatorOverlay(trendLines.support)
                .withLineColor(Color.GREEN)
                .withLineWidth(2.0f)
                .withOpacity(0.55f)
                .withLabel("Support (default)")
                .withIndicatorOverlay(trendLines.supportTouchBiased)
                .withLineColor(Color.BLUE)
                .withLineWidth(2.0f)
                .withOpacity(0.45f)
                .withLabel("Support (touchCountBiasPreset)")
                .withIndicatorOverlay(trendLines.supportExtremeBiased)
                .withLineColor(Color.MAGENTA)
                .withLineWidth(2.0f)
                .withOpacity(0.45f)
                .withLabel("Support (extremeSwingBiasPreset)")
                .withIndicatorOverlay(trendLines.resistance)
                .withLineColor(Color.RED)
                .withLineWidth(2.0f)
                .withOpacity(0.55f)
                .withLabel("Resistance (default)")
                .withIndicatorOverlay(trendLines.resistanceTouchBiased)
                .withLineColor(Color.CYAN)
                .withLineWidth(2.0f)
                .withOpacity(0.45f)
                .withLabel("Resistance (touchCountBiasPreset)")
                .withIndicatorOverlay(trendLines.resistanceExtremeBiased)
                .withLineColor(Color.ORANGE)
                .withLineWidth(2.0f)
                .withOpacity(0.45f)
                .withLabel("Resistance (extremeSwingBiasPreset)")
                .withIndicatorOverlay(swings.fractalLows)
                .withLineColor(Color.GREEN)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .withOpacity(0.85f)
                .withLabel("Fractal swing lows")
                .withIndicatorOverlay(swings.fractalHighs)
                .withLineColor(Color.RED)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .withOpacity(0.85f)
                .withLabel("Fractal swing highs")
                .withIndicatorOverlay(swings.zigzagLows)
                .withLineColor(Color.GREEN)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .withOpacity(0.25f)
                .withLabel("ZigZag swing lows")
                .withIndicatorOverlay(swings.zigzagHighs)
                .withLineColor(Color.RED)
                .withLineWidth(3.0f)
                .withConnectAcrossNaN(true)
                .withOpacity(0.25f)
                .withLabel("ZigZag swing highs")
                .toPlan();
    }

    private void renderAnalysisChart(AnalysisChartArtifacts artifacts, Config config) {
        int endIndex = artifacts.series().getEndIndex();

        TrendLineVariants trendLines = artifacts.trendLines();
        warmTrendLine(trendLines.support(), endIndex, "Support (default)");
        warmTrendLine(trendLines.supportTouchBiased(), endIndex, "Support (touchCountBiasPreset)");
        warmTrendLine(trendLines.supportExtremeBiased(), endIndex, "Support (extremeSwingBiasPreset)");

        warmTrendLine(trendLines.resistance(), endIndex, "Resistance (default)");
        warmTrendLine(trendLines.resistanceTouchBiased(), endIndex, "Resistance (touchCountBiasPreset)");
        warmTrendLine(trendLines.resistanceExtremeBiased(), endIndex, "Resistance (extremeSwingBiasPreset)");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        if (config.saveChart()) {
            chartWorkflow.save(artifacts.plan(), config.chartOutputDirectory(), config.chartFileName());
        }
        if (config.displayChart() && !GraphicsEnvironment.isHeadless()) {
            try {
                chartWorkflow.display(artifacts.plan());
            } catch (Exception e) {
                LOG.warn("Unable to display chart; continuing (headless runner?)", e);
            }
        }
    }

    private void warmTrendLine(AbstractTrendLineIndicator trendLine, int endIndex, String label) {
        trendLine.getValue(endIndex);
        TrendLineSegment segment = trendLine.getCurrentSegment();
        if (segment == null) {
            LOG.info("{} trendline: no active segment", label);
            return;
        }
        LOG.info(
                "{} trendline anchors=({}, {}), slope={}, intercept={}, swingTouches={}, swingsOutside={}, anchoredAtExtreme={}, score={}",
                label, segment.firstIndex, segment.secondIndex, segment.slope, segment.intercept, segment.touchCount,
                segment.outsideCount, segment.touchesExtreme, segment.score);
    }

    private record Config(String chartDatasetResource, String chartOutputDirectory, String chartFileName,
            boolean displayChart, boolean saveChart, int trendLineLookback, int surroundingBars) {

        private static Config fromArgs(String[] args) {
            String dataset = null;
            String outDir = DEFAULT_CHART_OUTPUT_DIRECTORY;
            String fileName = DEFAULT_CHART_FILE_NAME;
            boolean display = true;
            boolean save = true;
            int lookback = DEFAULT_TRENDLINE_LOOKBACK;
            int surroundingBars = DEFAULT_SURROUNDING_BARS;

            if (args != null) {
                for (String arg : args) {
                    if (arg == null || arg.isBlank()) {
                        continue;
                    }
                    if (arg.equals("--no-display")) {
                        display = false;
                        continue;
                    }
                    if (arg.equals("--no-save")) {
                        save = false;
                        continue;
                    }
                    if (arg.startsWith("--dataset=")) {
                        dataset = arg.substring("--dataset=".length());
                        continue;
                    }
                    if (arg.startsWith("--outDir=")) {
                        outDir = arg.substring("--outDir=".length());
                        continue;
                    }
                    if (arg.startsWith("--file=")) {
                        fileName = arg.substring("--file=".length());
                        continue;
                    }
                    if (arg.startsWith("--lookback=")) {
                        lookback = Integer.parseInt(arg.substring("--lookback=".length()));
                        continue;
                    }
                    if (arg.startsWith("--surroundingBars=")) {
                        surroundingBars = Integer.parseInt(arg.substring("--surroundingBars=".length()));
                    }
                }
            }

            return new Config(dataset, outDir, fileName, display, save, lookback, surroundingBars);
        }
    }

    private static void requireTrue(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }

    private static void requireFalse(boolean condition, String message) {
        requireTrue(!condition, message);
    }
}
