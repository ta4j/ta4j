/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave;

import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.util.ArrayList;
import java.time.Duration;
import java.util.Optional;
import java.time.Instant;
import java.util.Objects;
import java.awt.Color;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.ta4j.core.indicators.elliott.ElliottConfidenceScorer;
import org.ta4j.core.indicators.elliott.ElliottInvalidationIndicator;
import org.ta4j.core.indicators.elliott.ElliottConfluenceIndicator;
import org.ta4j.core.indicators.elliott.ElliottWaveCountIndicator;
import org.ta4j.core.indicators.elliott.ElliottScenarioIndicator;
import org.ta4j.core.indicators.elliott.ElliottChannelIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwingCompressor;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatioIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwingMetadata;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottWaveFacade;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottRatio;
import org.ta4j.core.indicators.elliott.ElliottTrendBias;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.PriceChannel;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.ChannelBoundaryIndicator;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.charting.builder.ChartPlan;

import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;
import ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;
import ta4jexamples.datasources.BarSeriesDataSource;

/**
 * Demonstrates the Elliott Wave indicator suite (swings, phases, Fibonacci
 * validation, channels, ratios, confluence scoring, invalidation checks) along
 * with chart visualisation and pivot labels.
 * <p>
 * This analysis class provides a comprehensive example of using the Elliott
 * Wave analysis framework, including:
 * <ul>
 * <li>Swing detection using ZigZag-based swing identification</li>
 * <li>Phase identification (impulse waves 1-5 and corrective waves A-B-C)</li>
 * <li>Fibonacci ratio validation with configurable tolerance</li>
 * <li>Channel projection (upper, lower, and median boundaries)</li>
 * <li>Confluence scoring (combining multiple Elliott Wave signals)</li>
 * <li>Scenario-based analysis with confidence scoring</li>
 * <li>Chart visualization with wave pivot labels</li>
 * </ul>
 * <p>
 * The class can be run from the command line with optional arguments to load
 * data from external sources, or it will use a default ossified dataset.
 * <p>
 * Command-line usage (optional):
 *
 * <pre>
 * java ElliottWaveAnalysis [dataSource] [ticker] [barDuration] [startEpoch] [endEpoch]
 * java ElliottWaveAnalysis [dataSource] [ticker] [barDuration] [degree] [startEpoch] [endEpoch]
 * </pre>
 *
 * Where:
 * <ul>
 * <li>{@code dataSource}: "YahooFinance" or "Coinbase"</li>
 * <li>{@code ticker}: Symbol (e.g., "BTC-USD", "AAPL")</li>
 * <li>{@code barDuration}: ISO-8601 duration (e.g., "PT1D" for daily)</li>
 * <li>{@code degree}: Elliott degree (optional; if omitted the recommendation
 * is based on bar duration and bar count)</li>
 * <li>{@code startEpoch}: Start time as Unix epoch seconds</li>
 * <li>{@code endEpoch}: End time as Unix epoch seconds (optional, defaults to
 * now)</li>
 * </ul>
 * <p>
 * If no arguments are provided, the class loads a default ossified dataset from
 * the classpath resources.
 * <p>
 * For simpler usage examples with specific assets, see:
 * <ul>
 * <li>{@link BTCUSDElliottWaveAnalysis} - Bitcoin analysis example</li>
 * <li>{@link ETHUSDElliottWaveAnalysis} - Ethereum analysis example</li>
 * <li>{@link SP500ElliottWaveAnalysis} - S&P 500 index analysis example</li>
 * <li>{@link ElliottWaveAdaptiveSwingAnalysis} - adaptive swing detection
 * demo</li>
 * <li>{@link ElliottWavePatternProfileDemo} - pattern-aware profile demo</li>
 * </ul>
 * <p>
 * For programmatic usage, see
 * {@link #analyze(BarSeries, ElliottDegree, double)} and
 * {@link #visualizeAnalysisResult(AnalysisResult)}.
 *
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @see org.ta4j.core.indicators.elliott.ElliottSwingIndicator
 * @see org.ta4j.core.indicators.elliott.ElliottPhaseIndicator
 * @see org.ta4j.core.indicators.elliott.ElliottScenarioIndicator
 * @see BTCUSDElliottWaveAnalysis
 * @see ETHUSDElliottWaveAnalysis
 * @see SP500ElliottWaveAnalysis
 */
public class ElliottWaveAnalysis {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnalysis.class);

    /**
     * Default OHLCV resource file loaded from classpath when no arguments provided.
     */
    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";
    /**
     * Default Elliott wave degree used when explicit parsing or auto selection
     * fails.
     */
    private static final ElliottDegree DEFAULT_DEGREE = ElliottDegree.PRIMARY;
    /** Default Fibonacci tolerance (0.25 = 25%) for phase validation. */
    private static final double DEFAULT_FIB_TOLERANCE = 0.25;

    /**
     * Main entry point for the Elliott Wave analysis demonstration.
     * <p>
     * Supports two modes of operation:
     * <ol>
     * <li><b>With arguments (4-6 args):</b> Loads data from an external data source
     * (YahooFinance or Coinbase) using the provided parameters.</li>
     * <li><b>Without arguments:</b> Loads a default ossified dataset from the
     * classpath resources.</li>
     * </ol>
     * <p>
     * When using external data sources, the required arguments are:
     * <ol>
     * <li>Data source name: "YahooFinance" or "Coinbase"</li>
     * <li>Ticker symbol: e.g., "BTC-USD", "AAPL"</li>
     * <li>Bar duration: ISO-8601 duration string (e.g., "PT1D", "PT4H",
     * "PT5M")</li>
     * <li>Elliott degree: One of the {@link ElliottDegree} enum values
     * (case-insensitive, optional). If omitted the degree is auto-selected based on
     * bar duration and bar count.</li>
     * <li>Start epoch: Unix timestamp in seconds</li>
     * <li>End epoch: Unix timestamp in seconds (optional, defaults to current
     * time)</li>
     * </ol>
     *
     * @param args command-line arguments (optional). If 4-6 arguments are provided,
     *             they are used to load data from an external source. Otherwise, a
     *             default dataset is loaded from resources.
     */
    public static void main(String[] args) {
        BarSeries series = loadBarSeries(args);
        if (series == null || series.isEmpty()) {
            if (series == null) {
                LOG.error("Bar series was null");
            } else {
                LOG.error("Series is empty, nothing to analyze.");
            }
            System.exit(1);
            return;
        }

        ElliottDegree degree = resolveDegree(args, series);
        ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
        AnalysisResult result = analysis.analyze(series, degree, DEFAULT_FIB_TOLERANCE);

        // Output structured result as JSON (without chart images by default)
        ElliottWaveAnalysisResult structuredResult = result.structuredResult();
        String json = structuredResult.toJson(false);
        LOG.info("Structured analysis result: {}", json);

        // Optionally visualize charts
        analysis.visualizeAnalysisResult(result);
    }

    /**
     * Loads a bar series based on command-line arguments or defaults.
     * <p>
     * If 4-6 arguments are provided, loads from an external data source. Otherwise,
     * loads the default ossified dataset from classpath resources.
     *
     * @param args command-line arguments
     * @return the loaded bar series, or {@code null} if loading fails
     */
    private static BarSeries loadBarSeries(String[] args) {
        if (args.length >= 4) {
            return loadBarSeriesFromArgs(args);
        } else {
            return loadSeries(DEFAULT_OHLCV_RESOURCE);
        }
    }

    /**
     * Parses command-line arguments and loads a bar series from an external data
     * source.
     *
     * @param args command-line arguments (must have at least 4 elements)
     * @return the loaded bar series, or {@code null} if parsing or loading fails
     */
    private static BarSeries loadBarSeriesFromArgs(String[] args) {
        Optional<BarSeriesRequest> request = parseBarSeriesRequest(args);
        if (request.isEmpty()) {
            return null;
        }
        BarSeriesRequest parsed = request.get();
        BarSeries series = loadSeriesFromDataSource(parsed.dataSource(), parsed.ticker(), parsed.barDuration(),
                parsed.startTime(), parsed.endTime());
        if (series == null) {
            LOG.error("Failed to retrieve bar series from {} for ticker {} with duration {} from {} to {}",
                    parsed.dataSource(), parsed.ticker(), parsed.barDuration(), parsed.startTime(), parsed.endTime());
        }
        return series;
    }

    static Optional<BarSeriesRequest> parseBarSeriesRequest(String[] args) {
        if (args.length < 4) {
            LOG.error(
                    "Insufficient arguments: expected at least 4, but got {}. Required: [dataSource] [ticker] [barDuration] [startEpoch] [endEpoch?] (optional degree before startEpoch)",
                    args.length);
            return Optional.empty();
        }

        String dataSource = args[0];
        if (dataSource == null || dataSource.trim().isEmpty()) {
            LOG.error(
                    "Invalid dataSource argument: cannot be null or empty. Provided value: '{}'. Supported sources: YahooFinance, Coinbase",
                    dataSource);
            return Optional.empty();
        }
        dataSource = dataSource.trim();
        if (!"YahooFinance".equalsIgnoreCase(dataSource) && !"Coinbase".equalsIgnoreCase(dataSource)) {
            LOG.error("Unsupported dataSource: '{}'. Supported sources: YahooFinance, Coinbase", dataSource);
            return Optional.empty();
        }

        String ticker = args[1];
        if (ticker == null || ticker.trim().isEmpty()) {
            LOG.error("Invalid ticker argument: cannot be null or empty. Provided value: '{}'", ticker);
            return Optional.empty();
        }
        ticker = ticker.trim();

        String barDurationStr = args[2];
        if (barDurationStr == null || barDurationStr.trim().isEmpty()) {
            LOG.error(
                    "Invalid barDuration argument: cannot be null or empty. Provided value: '{}'. Expected ISO-8601 duration format (e.g., PT1D, PT4H, PT5M)",
                    barDurationStr);
            return Optional.empty();
        }
        barDurationStr = barDurationStr.trim();

        String normalizedDurationStr = normalizeDurationString(barDurationStr);

        Duration barDuration;
        try {
            barDuration = Duration.parse(normalizedDurationStr);
            if (barDuration.isZero() || barDuration.isNegative()) {
                LOG.error("Invalid barDuration: '{}' must be a positive duration. Parsed value: {}", barDurationStr,
                        barDuration);
                return Optional.empty();
            }
        } catch (Exception ex) {
            LOG.error(
                    "Failed to parse barDuration '{}' as ISO-8601 duration. Error: {}. Expected format: PT1D (daily), PT4H (4-hour), PT5M (5-minute), etc. Note: Java Duration.parse() converts days to hours (PT1D becomes PT24H)",
                    barDurationStr, ex.getMessage());
            return Optional.empty();
        }

        boolean hasDegreeToken = hasDegreeToken(args);
        if (args.length >= 6 && isEpochSeconds(args[3])) {
            LOG.error(
                    "Invalid arguments: expected degree in position 4 when providing 6 arguments. Received epoch value '{}'",
                    args[3]);
            return Optional.empty();
        }

        int startEpochIndex = hasDegreeToken ? 4 : 3;

        String startEpochStr = args[startEpochIndex];
        if (startEpochStr == null || startEpochStr.trim().isEmpty()) {
            LOG.error(
                    "Invalid startEpoch argument: cannot be null or empty. Provided value: '{}'. Expected Unix timestamp in seconds",
                    startEpochStr);
            return Optional.empty();
        }
        startEpochStr = startEpochStr.trim();
        Instant startTime;
        try {
            long startEpochSeconds = Long.parseLong(startEpochStr);
            if (startEpochSeconds < 0) {
                LOG.error(
                        "Invalid startEpoch: '{}' must be a non-negative Unix timestamp (seconds since 1970-01-01). Provided value: {}",
                        startEpochStr, startEpochSeconds);
                return Optional.empty();
            }
            startTime = Instant.ofEpochSecond(startEpochSeconds);
        } catch (NumberFormatException ex) {
            LOG.error(
                    "Failed to parse startEpoch '{}' as a long integer. Error: {}. Expected Unix timestamp in seconds (e.g., 1686960000)",
                    startEpochStr, ex.getMessage());
            return Optional.empty();
        } catch (Exception ex) {
            LOG.error("Failed to create Instant from startEpoch '{}'. Error: {}", startEpochStr, ex.getMessage());
            return Optional.empty();
        }

        String endEpochStr = args.length > startEpochIndex + 1 ? args[startEpochIndex + 1] : null;
        Instant endTime;
        if (endEpochStr != null) {
            if (endEpochStr.trim().isEmpty()) {
                LOG.warn("endEpoch argument is empty, using current time");
                endTime = Instant.now();
            } else {
                endEpochStr = endEpochStr.trim();
                try {
                    long endEpochSeconds = Long.parseLong(endEpochStr);
                    if (endEpochSeconds < 0) {
                        LOG.error(
                                "Invalid endEpoch: '{}' must be a non-negative Unix timestamp (seconds since 1970-01-01). Provided value: {}",
                                endEpochStr, endEpochSeconds);
                        return Optional.empty();
                    }
                    endTime = Instant.ofEpochSecond(endEpochSeconds);
                } catch (NumberFormatException ex) {
                    LOG.error(
                            "Failed to parse endEpoch '{}' as a long integer. Error: {}. Expected Unix timestamp in seconds (e.g., 1697040000)",
                            endEpochStr, ex.getMessage());
                    return Optional.empty();
                } catch (Exception ex) {
                    LOG.error("Failed to create Instant from endEpoch '{}'. Error: {}", endEpochStr, ex.getMessage());
                    return Optional.empty();
                }
            }
        } else {
            endTime = Instant.now();
        }

        if (!startTime.isBefore(endTime)) {
            LOG.error("Invalid time range: startTime ({}) must be before endTime ({}). startEpoch: {}, endEpoch: {}",
                    startTime, endTime, startEpochStr, endEpochStr != null ? endEpochStr : "now");
            return Optional.empty();
        }

        return Optional.of(new BarSeriesRequest(dataSource, ticker, barDuration, startTime, endTime));
    }

    record BarSeriesRequest(String dataSource, String ticker, Duration barDuration, Instant startTime,
            Instant endTime) {
    }

    /**
     * Normalizes an ISO-8601 duration string for Java's Duration.parse().
     * <p>
     * Java's {@link Duration#parse(String)} doesn't support days (D) in the
     * ISO-8601 format, only hours (H), minutes (M), and seconds (S). This method
     * converts day-based durations to hour-based durations (e.g., PT1D -> PT24H,
     * PT2D -> PT48H).
     * <p>
     * If the duration string doesn't contain days, it's returned unchanged.
     *
     * @param durationStr the ISO-8601 duration string (e.g., "PT1D", "PT4H",
     *                    "PT5M")
     * @return the normalized duration string that can be parsed by
     *         {@link Duration#parse(String)}
     */
    private static String normalizeDurationString(String durationStr) {
        if (durationStr == null || durationStr.trim().isEmpty()) {
            return durationStr;
        }

        // Check if the duration contains days (D)
        // Pattern: PT followed by number and D (e.g., PT1D, PT2D)
        // Java's Duration.parse() doesn't support days, so convert to hours
        if (durationStr.contains("D") && durationStr.startsWith("PT")) {
            java.util.regex.Pattern dayPattern = java.util.regex.Pattern.compile("PT(\\d+)D");
            java.util.regex.Matcher matcher = dayPattern.matcher(durationStr);

            if (matcher.find()) {
                try {
                    int days = Integer.parseInt(matcher.group(1));
                    int hours = days * 24;
                    // Replace PT(\d+)D with PT(\d+)H
                    return durationStr.replaceFirst("PT\\d+D", "PT" + hours + "H");
                } catch (NumberFormatException e) {
                    LOG.warn("Failed to parse day component from duration string '{}'", durationStr, e);
                    // Fall back to returning the original duration string unchanged
                    return durationStr;
                }
            }
        }

        // No days found or pattern doesn't match, return as-is
        return durationStr;
    }

    /**
     * Resolves the Elliott degree from command-line arguments or auto-selects one
     * based on the series characteristics.
     *
     * @param args   command-line arguments
     * @param series bar series used for auto-selection
     * @return the resolved Elliott degree
     */
    static ElliottDegree resolveDegree(String[] args, BarSeries series) {
        Optional<ElliottDegree> explicitDegree = parseExplicitDegree(args);
        if (explicitDegree.isPresent()) {
            return explicitDegree.get();
        }
        return selectRecommendedDegree(series);
    }

    private static Optional<ElliottDegree> parseExplicitDegree(String[] args) {
        if (!hasDegreeToken(args)) {
            return Optional.empty();
        }
        String degreeValue = args[3];
        if (degreeValue == null || degreeValue.trim().isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(ElliottDegree.valueOf(degreeValue.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            LOG.warn("Invalid degree '{}', using default: {}", degreeValue, DEFAULT_DEGREE);
            return Optional.of(DEFAULT_DEGREE);
        }
    }

    private static ElliottDegree selectRecommendedDegree(BarSeries series) {
        if (series == null || series.isEmpty()) {
            LOG.warn("Series unavailable for degree selection, using default: {}", DEFAULT_DEGREE);
            return DEFAULT_DEGREE;
        }

        Duration barDuration = series.getFirstBar().getTimePeriod();
        if (barDuration == null || barDuration.isZero() || barDuration.isNegative()) {
            LOG.warn("Invalid bar duration '{}' for degree selection, using default: {}", barDuration, DEFAULT_DEGREE);
            return DEFAULT_DEGREE;
        }

        int barCount = series.getBarCount();
        try {
            List<ElliottDegree> recommendations = ElliottDegree.getRecommendedDegrees(barDuration, barCount);
            if (recommendations.isEmpty()) {
                LOG.warn("No recommended degrees for {} bars at {}, using default: {}", barCount, barDuration,
                        DEFAULT_DEGREE);
                return DEFAULT_DEGREE;
            }
            ElliottDegree selected = recommendations.get(0);
            LOG.info("Auto-selected Elliott degree {} for {} bars at {}. Candidates: {}", selected, barCount,
                    barDuration, recommendations);
            return selected;
        } catch (Exception ex) {
            LOG.warn("Failed to auto-select degree for {} bars at {}, using default: {}", barCount, barDuration,
                    DEFAULT_DEGREE, ex);
            return DEFAULT_DEGREE;
        }
    }

    private static boolean hasDegreeToken(String[] args) {
        if (args.length < 5) {
            return false;
        }
        if (args.length >= 6) {
            return true;
        }
        return !isEpochSeconds(args[3]);
    }

    private static boolean isEpochSeconds(String value) {
        if (value == null || value.trim().isEmpty()) {
            return false;
        }
        try {
            Long.parseLong(value.trim());
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    /**
     * Performs comprehensive Elliott Wave analysis on the provided bar series.
     * <p>
     * This method orchestrates the complete analysis workflow:
     * <ol>
     * <li>Sets up the Elliott Wave analysis framework (compressor and facade)</li>
     * <li>Retrieves all analysis indicators</li>
     * <li>Logs analysis results and scenario details</li>
     * <li>Builds chart plans for all scenarios (but does not display them)</li>
     * </ol>
     * <p>
     * This method can be called programmatically to perform Elliott Wave analysis
     * on any bar series without displaying charts. To visualize the results, call
     * {@link #visualizeAnalysisResult(AnalysisResult)} with the returned result.
     * <p>
     * Example usage:
     *
     * <pre>
     * BarSeries series = // ... load your bar series
     * ElliottWaveAnalysis analysis = new ElliottWaveAnalysis();
     * AnalysisResult result = analysis.analyze(series, ElliottDegree.PRIMARY, 0.25);
     * // Optionally visualize
     * analysis.visualizeAnalysisResult(result);
     * </pre>
     *
     * @param series       the bar series to analyze (must not be null or empty)
     * @param degree       the Elliott wave degree to use for swing detection
     * @param fibTolerance the Fibonacci tolerance (0.0-1.0) for phase validation.
     *                     Higher values allow more deviation from ideal Fibonacci
     *                     ratios. Default is 0.25 (25%).
     * @return an {@link AnalysisResult} containing all analysis findings and chart
     *         plans
     * @throws NullPointerException     if series is null
     * @throws IllegalArgumentException if series is empty
     */
    public AnalysisResult analyze(BarSeries series, ElliottDegree degree, double fibTolerance) {
        Objects.requireNonNull(series, "Series cannot be null");
        if (series.isEmpty()) {
            throw new IllegalArgumentException("Series cannot be empty");
        }

        ElliottWaveFacade facade = createElliottWaveFacade(series, degree, fibTolerance);
        int endIndex = series.getEndIndex();

        // Extract all indicators from facade
        ElliottPhaseIndicator phaseIndicator = facade.phase();
        ElliottInvalidationIndicator invalidationIndicator = facade.invalidation();
        ElliottChannelIndicator channelIndicator = facade.channel();
        ElliottRatioIndicator ratioIndicator = facade.ratio();
        ElliottConfluenceIndicator confluenceIndicator = facade.confluence();
        ElliottWaveCountIndicator swingCount = facade.waveCount();
        ElliottWaveCountIndicator filteredSwingCount = facade.filteredWaveCount();
        ElliottScenarioIndicator scenarioIndicator = facade.scenarios();
        ElliottSwingMetadata swingMetadata = ElliottSwingMetadata.of(facade.swing().getValue(endIndex),
                series.numFactory());
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(endIndex);
        ElliottTrendBias trendBias = scenarioSet.trendBias();

        // Create chart indicators
        Indicator<Num> ratioValue = new RatioValueIndicator(series, ratioIndicator, "Elliott ratio value");
        Indicator<Num> swingCountAsNum = new IntegerAsNumIndicator(series, swingCount, "Swings (raw)");
        Indicator<Num> filteredSwingCountAsNum = new IntegerAsNumIndicator(series, filteredSwingCount,
                "Swings (compressed)");

        // Build chart plans for all scenarios
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        Optional<ChartPlan> baseCaseChartPlan = Optional.empty();
        List<ChartPlan> alternativeChartPlans = new ArrayList<>();

        String trendLabel = formatTrendBiasLabel(trendBias);
        if (scenarioSet.base().isPresent()) {
            ElliottScenario baseCase = scenarioSet.base().get();
            String baseCaseLabel = "BASE CASE";
            String baseCaseTitle = buildScenarioTitle(degree, series, trendLabel, baseCaseLabel, baseCase);
            BarSeriesLabelIndicator baseCaseWaveLabels = buildWaveLabelsFromScenario(series, baseCase);
            baseCaseChartPlan = Optional.of(buildChartPlan(chartWorkflow, series, channelIndicator, baseCaseWaveLabels,
                    swingCountAsNum, filteredSwingCountAsNum, ratioValue, confluenceIndicator, baseCaseTitle));
        }

        List<ElliottScenario> alternatives = scenarioSet.alternatives();
        for (int i = 0; i < alternatives.size(); i++) {
            ElliottScenario alt = alternatives.get(i);
            String altLabel = String.format("ALTERNATIVE %d", i + 1);
            String altTitle = buildScenarioTitle(degree, series, trendLabel, altLabel, alt);
            BarSeriesLabelIndicator altWaveLabels = buildWaveLabelsFromScenario(series, alt);
            alternativeChartPlans.add(buildChartPlan(chartWorkflow, series, channelIndicator, altWaveLabels,
                    swingCountAsNum, filteredSwingCountAsNum, ratioValue, confluenceIndicator, altTitle));
        }

        // Create structured analysis result with embedded chart images
        ElliottWaveAnalysisResult structuredResult = ElliottWaveAnalysisResult.from(degree, swingMetadata,
                phaseIndicator, ratioIndicator, channelIndicator, confluenceIndicator, invalidationIndicator,
                scenarioSet, endIndex, baseCaseChartPlan, alternativeChartPlans);

        // Log the structured result (for backward compatibility)
        logStructuredAnalysisResult(structuredResult, scenarioSet, series.numFactory());

        return new AnalysisResult(series, degree, endIndex, phaseIndicator, invalidationIndicator, channelIndicator,
                ratioIndicator, confluenceIndicator, swingCount, filteredSwingCount, scenarioIndicator, swingMetadata,
                scenarioSet, ratioValue, swingCountAsNum, filteredSwingCountAsNum, baseCaseChartPlan,
                alternativeChartPlans, structuredResult);
    }

    /**
     * Visualizes the analysis results by displaying and saving charts for all
     * scenarios.
     * <p>
     * Charts are saved to {@code temp/charts/} directory and displayed if running
     * in a non-headless environment.
     * <p>
     * This method processes the chart plans contained in the analysis result and
     * renders them using JFreeChart. The base case scenario chart (if present) is
     * displayed first, followed by alternative scenario charts.
     *
     * @param result the analysis result containing chart plans and analysis data
     * @throws NullPointerException if result is null
     */
    public void visualizeAnalysisResult(AnalysisResult result) {
        Objects.requireNonNull(result, "Analysis result cannot be null");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        boolean isHeadless = GraphicsEnvironment.isHeadless();
        String trendLabel = formatTrendBiasLabel(result.structuredResult().trendBias());

        // Display and save base case scenario chart
        if (result.baseCaseChartPlan().isPresent()) {
            ChartPlan baseCasePlan = result.baseCaseChartPlan().get();
            ElliottScenario baseCase = result.scenarioSet().base().orElseThrow();
            String baseCaseLabel = "BASE CASE";
            String baseCaseWindowTitle = buildScenarioWindowTitle(result.degree(), trendLabel, baseCaseLabel, baseCase,
                    result.series().getName());

            if (!isHeadless) {
                chartWorkflow.display(baseCasePlan, baseCaseWindowTitle);
            }
            chartWorkflow.save(baseCasePlan, "temp/charts",
                    "elliott-wave-analysis-" + result.series().getName().toLowerCase() + "-"
                            + result.degree().name().toLowerCase() + "-base-case");
        }

        // Display and save alternative scenario charts
        List<ElliottScenario> alternatives = result.scenarioSet().alternatives();
        for (int i = 0; i < result.alternativeChartPlans().size() && i < alternatives.size(); i++) {
            ChartPlan altPlan = result.alternativeChartPlans().get(i);
            ElliottScenario alt = alternatives.get(i);
            String altLabel = String.format("ALTERNATIVE %d", i + 1);
            String altWindowTitle = buildScenarioWindowTitle(result.degree(), trendLabel, altLabel, alt,
                    result.series().getName());

            if (!isHeadless) {
                chartWorkflow.display(altPlan, altWindowTitle);
            }
            chartWorkflow.save(altPlan, "temp/charts",
                    "elliott-wave-analysis-" + result.series().getName().toLowerCase() + "-"
                            + result.degree().name().toLowerCase() + "-alternative-" + (i + 1));
        }
    }

    /**
     * Creates a swing compressor and Elliott Wave facade for analysis.
     * <p>
     * The compressor filters out noise by requiring:
     * <ul>
     * <li>Minimum amplitude: 1% of current price (relative threshold that scales
     * with asset price)</li>
     * <li>Minimum length: 2 bars (ensures swings represent actual price movements
     * over time)</li>
     * </ul>
     * <p>
     * This filtering is essential for higher-degree analysis (PRIMARY,
     * INTERMEDIATE) where we want to identify major trend structures, not minor
     * intraday fluctuations.
     *
     * @param series       the bar series to analyze
     * @param degree       the Elliott wave degree
     * @param fibTolerance the Fibonacci tolerance for phase validation
     * @return the configured Elliott Wave facade
     */
    private static ElliottWaveFacade createElliottWaveFacade(BarSeries series, ElliottDegree degree,
            double fibTolerance) {
        ElliottSwingCompressor compressor = new ElliottSwingCompressor(series);
        return ElliottWaveFacade.zigZag(series, degree, Optional.of(series.numFactory().numOf(fibTolerance)),
                Optional.of(compressor));
    }

    /**
     * Logs the structured analysis result, replacing the previous logging methods.
     * <p>
     * This method logs all the data captured in {@link ElliottWaveAnalysisResult},
     * maintaining backward compatibility with the previous logging format.
     *
     * @param result the structured analysis result
     */
    private static void logStructuredAnalysisResult(ElliottWaveAnalysisResult result, ElliottScenarioSet scenarioSet,
            NumFactory numFactory) {
        // Log swing snapshot
        ElliottWaveAnalysisResult.SwingSnapshot snapshot = result.swingSnapshot();
        LOG.info("Elliott swing snapshot valid={}, swings={}, high={}, low={}", snapshot.valid(), snapshot.swings(),
                snapshot.high(), snapshot.low());

        // Log latest analysis
        ElliottWaveAnalysisResult.LatestAnalysis latest = result.latestAnalysis();
        LOG.info("Latest phase={} impulseConfirmed={} correctiveConfirmed={}", latest.phase(),
                latest.impulseConfirmed(), latest.correctiveConfirmed());
        LOG.info("Latest ratio type={} value={}", latest.ratioType(), latest.ratioValue());
        if (latest.channel() != null) {
            LOG.info("Latest channel valid={} upper={} lower={} median={}", latest.channel().valid(),
                    latest.channel().upper(), latest.channel().lower(), latest.channel().median());
        } else {
            LOG.info("Latest channel valid=false");
        }
        LOG.info("Latest confluence score={} confluent={}", latest.confluenceScore(), latest.confluent());
        LOG.info("Latest invalidation={}", latest.invalidation());

        // Log scenario summary
        ElliottWaveAnalysisResult.ScenarioSummary summary = result.scenarioSummary();
        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", summary.summary());
        LOG.info("Strong consensus: {} | Consensus phase: {}", summary.strongConsensus(), summary.consensusPhase());
        logTrendBias(result.trendBias());

        // Log base case scenario
        if (result.baseCase() != null) {
            ElliottWaveAnalysisResult.BaseCaseScenario baseCase = result.baseCase();
            LOG.info("BASE CASE SCENARIO: {} ({})", baseCase.currentPhase(), baseCase.type());
            LOG.info("  Overall confidence: {}% ({})", String.format("%.1f", baseCase.overallConfidence()),
                    baseCase.confidenceLevel());
            LOG.info("  Scenario probability: {}%", String.format("%.1f", baseCase.scenarioProbability() * 100.0));
            LOG.info("  Factor scores: Fibonacci={}% | Time={}% | Alternation={}% | Channel={}% | Completeness={}%",
                    String.format("%.1f", baseCase.fibonacciScore()), String.format("%.1f", baseCase.timeScore()),
                    String.format("%.1f", baseCase.alternationScore()), String.format("%.1f", baseCase.channelScore()),
                    String.format("%.1f", baseCase.completenessScore()));
            LOG.info("  Primary reason: {}", baseCase.primaryReason());
            LOG.info("  Weakest factor: {}", baseCase.weakestFactor());
            LOG.info("  Direction: {} | Invalidation: {} | Target: {}", baseCase.direction(),
                    baseCase.invalidationPrice(), baseCase.primaryTarget());
            scenarioSet.base().ifPresent(scenario -> logTimeAlternationDiagnostics(numFactory, scenario));
        }

        // Log alternative scenarios
        List<ElliottWaveAnalysisResult.AlternativeScenario> alternatives = result.alternatives();
        if (!alternatives.isEmpty()) {
            LOG.info("ALTERNATIVE SCENARIOS ({}):", alternatives.size());
            for (int i = 0; i < Math.min(alternatives.size(), 3); i++) {
                ElliottWaveAnalysisResult.AlternativeScenario alt = alternatives.get(i);
                LOG.info("  {}. {} ({}) - {}% confidence | {}% probability", i + 1, alt.currentPhase(), alt.type(),
                        String.format("%.1f", alt.confidencePercent()),
                        String.format("%.1f", alt.scenarioProbability() * 100.0));
            }
        }
        LOG.info("======================================");
    }

    /**
     * Loads a bar series from a JSON resource file in the classpath.
     * <p>
     * The resource file should be in JSON format compatible with
     * {@link JsonFileBarSeriesDataSource}. The loaded series is copied into a new
     * series with a descriptive name for charting purposes.
     *
     * @param resource the classpath resource path to the JSON file containing OHLCV
     *                 data
     * @return the loaded bar series, or {@code null} if the resource is missing or
     *         cannot be loaded
     */
    private static BarSeries loadSeries(String resource) {
        try (InputStream stream = ElliottWaveAnalysis.class.getClassLoader().getResourceAsStream(resource)) {
            if (stream == null) {
                LOG.error("Missing resource: {}", resource);
                return null;
            }
            BarSeries loaded = JsonFileBarSeriesDataSource.DEFAULT_INSTANCE.loadSeries(stream);
            if (loaded == null) {
                return null;
            }

            BarSeries series = new BaseBarSeriesBuilder().withName("BTC-USD_PT1D@Coinbase (daily)").build();
            for (int i = 0; i < loaded.getBarCount(); i++) {
                series.addBar(loaded.getBar(i));
            }
            return series;
        } catch (Exception ex) {
            LOG.error("Failed to load Elliott wave dataset: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Loads a bar series from an external data source (YahooFinance or Coinbase).
     * <p>
     * This method supports loading historical OHLCV data from:
     * <ul>
     * <li><b>YahooFinance:</b> Stock and ETF data via Yahoo Finance API</li>
     * <li><b>Coinbase:</b> Cryptocurrency data via Coinbase API</li>
     * </ul>
     * <p>
     * The data source is configured with response caching enabled to improve
     * performance and reduce API calls.
     *
     * @param dataSource  the data source name ("YahooFinance" or "Coinbase",
     *                    case-insensitive)
     * @param ticker      the ticker symbol (e.g., "BTC-USD", "AAPL")
     * @param barDuration the duration of each bar (e.g., PT1D for daily, PT4H for
     *                    4-hour, PT5M for 5-minute)
     * @param startTime   the start time for the data range
     * @param endTime     the end time for the data range
     * @return the loaded bar series, or {@code null} if the data source is
     *         unsupported, the request fails, or the returned series is empty
     */
    static BarSeries loadSeriesFromDataSource(String dataSource, String ticker, Duration barDuration, Instant startTime,
            Instant endTime) {
        try {
            BarSeriesDataSource source;
            if ("YahooFinance".equalsIgnoreCase(dataSource)) {
                source = new YahooFinanceHttpBarSeriesDataSource(true);
            } else if ("Coinbase".equalsIgnoreCase(dataSource)) {
                source = new CoinbaseHttpBarSeriesDataSource(true);
            } else {
                LOG.error("Unsupported data source: {}. Supported sources: YahooFinance, Coinbase", dataSource);
                return null;
            }
            return loadSeriesFromDataSource(source, ticker, barDuration, startTime, endTime);
        } catch (Exception ex) {
            LOG.error("Exception while loading series from {}: {}", dataSource, ex.getMessage(), ex);
            return null;
        }
    }

    static BarSeries loadSeriesFromDataSource(BarSeriesDataSource source, String ticker, Duration barDuration,
            Instant startTime, Instant endTime) {
        Objects.requireNonNull(source, "source");
        String sourceName = source.getSourceName();
        if (sourceName == null || sourceName.isBlank()) {
            sourceName = source.getClass().getSimpleName();
        }
        try {
            BarSeries series = source.loadSeries(ticker, barDuration, startTime, endTime);
            if (series == null) {
                LOG.error("Data source returned null for ticker {} with duration {} from {} to {}", ticker, barDuration,
                        startTime, endTime);
                return null;
            }

            if (series.isEmpty()) {
                LOG.error("Data source returned empty series for ticker {} with duration {} from {} to {}", ticker,
                        barDuration, startTime, endTime);
                return null;
            }

            return series;
        } catch (Exception ex) {
            LOG.error("Exception while loading series from {}: {}", sourceName, ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Builds a chart plan for visualizing Elliott Wave analysis results.
     * <p>
     * The chart includes:
     * <ul>
     * <li><b>Main chart:</b> Price bars with Elliott channel boundaries (upper,
     * lower, median) and wave pivot labels</li>
     * <li><b>Swing count subchart:</b> Raw swing count with filtered (compressed)
     * swing count overlay</li>
     * <li><b>Ratio subchart:</b> Elliott ratio values with horizontal marker at
     * 1.0</li>
     * <li><b>Confluence subchart:</b> Confluence scores with horizontal marker at
     * 2.0</li>
     * </ul>
     * <p>
     * Channel boundaries use a muted shared color with semi-transparent strokes,
     * and the channel interior is lightly shaded to match.
     *
     * @param chartWorkflow           the chart workflow instance for building
     *                                charts
     * @param series                  the bar series to chart
     * @param channelIndicator        indicator providing channel boundaries
     * @param waveLabels              indicator providing wave pivot labels (1-5 for
     *                                impulses, A-B-C for corrections)
     * @param swingCountAsNum         indicator for raw swing count (as numeric)
     * @param filteredSwingCountAsNum indicator for filtered swing count (as
     *                                numeric)
     * @param ratioValue              indicator for Elliott ratio values
     * @param confluenceIndicator     indicator for confluence scores
     * @param title                   the chart title
     * @return a configured chart plan ready for display or saving
     */
    private static ChartPlan buildChartPlan(ChartWorkflow chartWorkflow, BarSeries series,
            Indicator<? extends PriceChannel> channelIndicator, BarSeriesLabelIndicator waveLabels,
            Indicator<Num> swingCountAsNum, Indicator<Num> filteredSwingCountAsNum, Indicator<Num> ratioValue,
            Indicator<Num> confluenceIndicator, String title) {
        ChannelBoundaryIndicator channelUpper = new ChannelBoundaryIndicator(channelIndicator,
                PriceChannel.Boundary.UPPER, "Elliott channel upper");
        ChannelBoundaryIndicator channelLower = new ChannelBoundaryIndicator(channelIndicator,
                PriceChannel.Boundary.LOWER, "Elliott channel lower");
        ChannelBoundaryIndicator channelMedian = new ChannelBoundaryIndicator(channelIndicator,
                PriceChannel.Boundary.MEDIAN, "Elliott channel median");
        return chartWorkflow.builder()
                .withTitle(title)
                .withSeries(series)
                .withChannelOverlay(channelUpper, channelMedian, channelLower)
                .withIndicatorOverlay(waveLabels)
                .withLineColor(new Color(0x40FFFF)) // Brighter cyan for better visibility
                .withLineWidth(2.0f)
                .withOpacity(1.0f) // Full opacity for maximum visibility
                .withLabel("Wave pivots")
                .withSubChart(swingCountAsNum)
                .withIndicatorOverlay(filteredSwingCountAsNum)
                .withLineColor(new Color(0x90CAF9))
                .withOpacity(0.85f)
                .withLabel("Swings (compressed)")
                .withSubChart(ratioValue)
                .withHorizontalMarker(1.0)
                .withLineColor(Color.GRAY)
                .withOpacity(0.3f)
                .withSubChart(confluenceIndicator)
                .withHorizontalMarker(2.0)
                .withLineColor(Color.GRAY)
                .withOpacity(0.3f)
                .toPlan();
    }

    /**
     * Builds wave pivot labels from an Elliott scenario's swing sequence.
     * <p>
     * Labels are assigned based on the scenario type:
     * <ul>
     * <li><b>Impulse patterns:</b> Labels are numbered 1, 2, 3, 4, 5 corresponding
     * to the five waves of an impulse pattern</li>
     * <li><b>Corrective patterns:</b> Labels are lettered A, B, C corresponding to
     * the three waves of a corrective pattern</li>
     * <li><b>Unknown patterns:</b> Labels are numbered sequentially (1, 2, 3,
     * ...)</li>
     * </ul>
     * <p>
     * Label placement is determined by the pivot type:
     * <ul>
     * <li>High pivots (rising swings): Labels placed above the pivot</li>
     * <li>Low pivots (falling swings): Labels placed below the pivot</li>
     * </ul>
     *
     * @param series   the bar series (used for creating the label indicator)
     * @param scenario the Elliott scenario containing the swing sequence to label
     * @return a label indicator configured with wave pivot labels for the scenario
     */
    private static BarSeriesLabelIndicator buildWaveLabelsFromScenario(BarSeries series, ElliottScenario scenario) {
        List<ElliottSwing> swings = scenario.swings();
        if (swings.isEmpty()) {
            return new BarSeriesLabelIndicator(series, new ArrayList<>());
        }

        List<BarLabel> labels = new ArrayList<>();
        ScenarioType type = scenario.type();

        addFirstSwingLabel(swings, labels);

        if (type.isImpulse()) {
            addImpulseWaveLabels(swings, labels);
        } else if (type.isCorrective()) {
            addCorrectiveWaveLabels(swings, labels);
        } else {
            addSequentialWaveLabels(swings, labels);
        }

        return new BarSeriesLabelIndicator(series, labels);
    }

    /**
     * Adds a label for the first swing's starting point (typically unlabeled).
     *
     * @param swings the list of swings
     * @param labels the list of labels to append to
     */
    private static void addFirstSwingLabel(List<ElliottSwing> swings, List<BarLabel> labels) {
        if (!swings.isEmpty()) {
            ElliottSwing first = swings.get(0);
            labels.add(new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
        }
    }

    /**
     * Adds labels for impulse wave pattern (numbered 1, 2, 3, 4, 5).
     *
     * @param swings the list of swings
     * @param labels the list of labels to append to
     */
    private static void addImpulseWaveLabels(List<ElliottSwing> swings, List<BarLabel> labels) {
        for (int i = 0; i < swings.size(); i++) {
            ElliottSwing swing = swings.get(i);
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                    placementForPivot(swing.isRising())));
        }
    }

    /**
     * Adds labels for corrective wave pattern (lettered A, B, C).
     *
     * @param swings the list of swings
     * @param labels the list of labels to append to
     */
    private static void addCorrectiveWaveLabels(List<ElliottSwing> swings, List<BarLabel> labels) {
        for (int i = 0; i < swings.size(); i++) {
            ElliottSwing swing = swings.get(i);
            String label = switch (i) {
            case 0 -> "A";
            case 1 -> "B";
            default -> "C";
            };
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), label, placementForPivot(swing.isRising())));
        }
    }

    /**
     * Adds labels for unknown pattern types (numbered sequentially 1, 2, 3, ...).
     *
     * @param swings the list of swings
     * @param labels the list of labels to append to
     */
    private static void addSequentialWaveLabels(List<ElliottSwing> swings, List<BarLabel> labels) {
        for (int i = 0; i < swings.size(); i++) {
            ElliottSwing swing = swings.get(i);
            labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                    placementForPivot(swing.isRising())));
        }
    }

    /**
     * Determines the label placement for a pivot point based on whether it's a high
     * or low pivot.
     *
     * @param isHighPivot {@code true} if the pivot is a high (peak), {@code false}
     *                    if it's a low (trough)
     * @return {@link LabelPlacement#ABOVE} for high pivots,
     *         {@link LabelPlacement#BELOW} for low pivots
     */
    private static LabelPlacement placementForPivot(boolean isHighPivot) {
        return isHighPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    private static String buildScenarioTitle(ElliottDegree degree, BarSeries series, String trendLabel,
            String scenarioLabel, ElliottScenario scenario) {
        return String.format("Elliott Wave (%s) - %s - %s - %s: %s (%s) - %.1f%% confidence", degree, series.getName(),
                trendLabel, scenarioLabel, scenario.currentPhase(), scenario.type(),
                scenario.confidence().asPercentage());
    }

    private static String buildScenarioWindowTitle(ElliottDegree degree, String trendLabel, String scenarioLabel,
            ElliottScenario scenario, String seriesName) {
        return String.format("%s - %s - %s: %s (%s) - %.1f%% - %s", degree, trendLabel, scenarioLabel,
                scenario.currentPhase(), scenario.type(), scenario.confidence().asPercentage(), seriesName);
    }

    private static String formatTrendBiasLabel(ElliottTrendBias trendBias) {
        if (trendBias == null || trendBias.isUnknown()) {
            return "TREND: UNKNOWN";
        }
        if (trendBias.isNeutral()) {
            return "TREND: NEUTRAL";
        }
        double strength = trendBias.strength();
        if (Double.isNaN(strength)) {
            return "TREND: " + trendBias.direction();
        }
        return String.format("TREND: %s (%.0f%%)", trendBias.direction(), strength * 100.0);
    }

    private static void logTrendBias(ElliottTrendBias trendBias) {
        if (trendBias == null) {
            LOG.info("Trend bias: UNKNOWN");
            return;
        }
        String score = Double.isNaN(trendBias.score()) ? "n/a" : String.format("%.3f", trendBias.score());
        String strength = Double.isNaN(trendBias.strength()) ? "n/a"
                : String.format("%.1f%%", trendBias.strength() * 100.0);
        LOG.info("Trend bias: {} | score={} | strength={} | consensus={} | directionalScenarios={}/{}",
                trendBias.direction(), score, strength, trendBias.consensus(), trendBias.knownDirectionCount(),
                trendBias.totalScenarios());
    }

    private static void logTimeAlternationDiagnostics(NumFactory numFactory, ElliottScenario scenario) {
        if (numFactory == null || scenario == null) {
            return;
        }
        ElliottConfidenceScorer scorer = new ElliottConfidenceScorer(numFactory);
        ElliottConfidenceScorer.AlternationDiagnostics diagnostics = scorer.alternationDiagnostics(scenario.swings(),
                scenario.currentPhase());
        String ratio = Double.isNaN(diagnostics.durationRatio()) ? "n/a"
                : String.format("%.2f", diagnostics.durationRatio());
        LOG.info("  Time alternation: barsW2={} | barsW4={} | ratio={} | depthDiff={} | timeDiff={} | score={}",
                diagnostics.barsWave2(), diagnostics.barsWave4(), ratio,
                String.format("%.2f", diagnostics.depthDifference()),
                String.format("%.2f", diagnostics.timeDifference()), String.format("%.2f", diagnostics.score()));
    }

    /**
     * Indicator that extracts the numeric ratio value from an Elliott ratio
     * indicator.
     * <p>
     * The {@link ElliottRatioIndicator} returns {@link ElliottRatio} records
     * containing both the ratio value and type. This wrapper extracts just the
     * numeric value for use in charting.
     */
    private static final class RatioValueIndicator extends CachedIndicator<Num> {

        private final ElliottRatioIndicator ratioIndicator;
        private final String label;

        /**
         * Creates a ratio value indicator.
         *
         * @param series         the bar series
         * @param ratioIndicator the Elliott ratio indicator to extract values from
         * @param label          the display label for this indicator
         */
        private RatioValueIndicator(BarSeries series, ElliottRatioIndicator ratioIndicator, String label) {
            super(series);
            this.ratioIndicator = Objects.requireNonNull(ratioIndicator, "ratioIndicator");
            this.label = Objects.requireNonNull(label, "label");
        }

        @Override
        protected Num calculate(int index) {
            ElliottRatio ratio = ratioIndicator.getValue(index);
            if (ratio == null) {
                return getBarSeries().numFactory().numOf(Double.NaN);
            }
            return ratio.value();
        }

        @Override
        public int getCountOfUnstableBars() {
            return ratioIndicator.getCountOfUnstableBars();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Indicator that converts an integer indicator to a numeric indicator.
     * <p>
     * This wrapper allows integer-based indicators (such as
     * {@link ElliottWaveCountIndicator}) to be used in charting contexts that
     * require numeric indicators.
     */
    private static final class IntegerAsNumIndicator extends CachedIndicator<Num> {

        private final Indicator<Integer> delegate;
        private final String label;

        /**
         * Creates an integer-to-numeric indicator wrapper.
         *
         * @param series   the bar series
         * @param delegate the integer indicator to wrap
         * @param label    the display label for this indicator
         */
        private IntegerAsNumIndicator(BarSeries series, Indicator<Integer> delegate, String label) {
            super(series);
            this.delegate = Objects.requireNonNull(delegate, "delegate");
            this.label = Objects.requireNonNull(label, "label");
        }

        @Override
        protected Num calculate(int index) {
            Integer value = delegate.getValue(index);
            return value == null ? getBarSeries().numFactory().numOf(Double.NaN)
                    : getBarSeries().numFactory().numOf(value);
        }

        @Override
        public int getCountOfUnstableBars() {
            return delegate.getCountOfUnstableBars();
        }

        @Override
        public String toString() {
            return label;
        }
    }

    /**
     * Result container for Elliott Wave analysis findings.
     * <p>
     * This record holds all analysis indicators, metadata, and chart plans
     * generated during Elliott Wave analysis. It allows separation of analysis
     * computation from visualization.
     *
     * @param series                  the analyzed bar series
     * @param degree                  the Elliott wave degree used
     * @param endIndex                the index at which analysis was evaluated
     * @param phaseIndicator          the phase indicator
     * @param invalidationIndicator   the invalidation indicator
     * @param channelIndicator        the channel indicator
     * @param ratioIndicator          the ratio indicator
     * @param confluenceIndicator     the confluence indicator
     * @param swingCount              the raw swing count indicator
     * @param filteredSwingCount      the filtered swing count indicator
     * @param scenarioIndicator       the scenario indicator
     * @param swingMetadata           the swing metadata snapshot
     * @param scenarioSet             the scenario set at endIndex (use
     *                                {@code scenarioSet.base()} to get the base
     *                                case scenario)
     * @param ratioValue              indicator for Elliott ratio values (for
     *                                charting)
     * @param swingCountAsNum         indicator for raw swing count (as numeric, for
     *                                charting)
     * @param filteredSwingCountAsNum indicator for filtered swing count (as
     *                                numeric, for charting)
     * @param baseCaseChartPlan       chart plan for the base case scenario (if
     *                                present)
     * @param alternativeChartPlans   chart plans for alternative scenarios
     * @param structuredResult        structured analysis result with embedded chart
     *                                images and all logged data
     */
    public record AnalysisResult(BarSeries series, ElliottDegree degree, int endIndex,
            ElliottPhaseIndicator phaseIndicator, ElliottInvalidationIndicator invalidationIndicator,
            ElliottChannelIndicator channelIndicator, ElliottRatioIndicator ratioIndicator,
            ElliottConfluenceIndicator confluenceIndicator, ElliottWaveCountIndicator swingCount,
            ElliottWaveCountIndicator filteredSwingCount, ElliottScenarioIndicator scenarioIndicator,
            ElliottSwingMetadata swingMetadata, ElliottScenarioSet scenarioSet, Indicator<Num> ratioValue,
            Indicator<Num> swingCountAsNum, Indicator<Num> filteredSwingCountAsNum,
            Optional<ChartPlan> baseCaseChartPlan, List<ChartPlan> alternativeChartPlans,
            ElliottWaveAnalysisResult structuredResult) {
    }

}
