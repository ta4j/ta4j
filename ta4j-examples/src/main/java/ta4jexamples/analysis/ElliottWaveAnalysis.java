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

import java.awt.Color;
import java.awt.GraphicsEnvironment;
import java.io.InputStream;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottChannelIndicator;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottConfluenceIndicator;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottInvalidationIndicator;
import org.ta4j.core.indicators.elliott.ElliottPhaseIndicator;
import org.ta4j.core.indicators.elliott.ElliottRatio;
import org.ta4j.core.indicators.elliott.ElliottRatioIndicator;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioIndicator;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottSwingCompressor;
import org.ta4j.core.indicators.elliott.ElliottSwingMetadata;
import org.ta4j.core.indicators.elliott.ElliottWaveCountIndicator;
import org.ta4j.core.indicators.elliott.ElliottWaveFacade;
import org.ta4j.core.num.Num;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;
import ta4jexamples.datasources.BarSeriesDataSource;
import ta4jexamples.datasources.CoinbaseHttpBarSeriesDataSource;
import ta4jexamples.datasources.JsonFileBarSeriesDataSource;
import ta4jexamples.datasources.YahooFinanceHttpBarSeriesDataSource;

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
 * java ElliottWaveAnalysis [dataSource] [ticker] [barDuration] [degree] [startEpoch] [endEpoch]
 * </pre>
 *
 * Where:
 * <ul>
 * <li>{@code dataSource}: "YahooFinance" or "Coinbase"</li>
 * <li>{@code ticker}: Symbol (e.g., "BTC-USD", "AAPL")</li>
 * <li>{@code barDuration}: ISO-8601 duration (e.g., "PT1D" for daily)</li>
 * <li>{@code degree}: Elliott degree (e.g., "PRIMARY", "INTERMEDIATE")</li>
 * <li>{@code startEpoch}: Start time as Unix epoch seconds</li>
 * <li>{@code endEpoch}: End time as Unix epoch seconds (optional, defaults to
 * now)</li>
 * </ul>
 * <p>
 * If no arguments are provided, the class loads a default ossified dataset from
 * the classpath resources.
 *
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @see org.ta4j.core.indicators.elliott.ElliottSwingIndicator
 * @see org.ta4j.core.indicators.elliott.ElliottPhaseIndicator
 * @see org.ta4j.core.indicators.elliott.ElliottScenarioIndicator
 */
public class ElliottWaveAnalysis {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnalysis.class);

    /**
     * Default OHLCV resource file loaded from classpath when no arguments provided.
     */
    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";
    /** Default Elliott wave degree used for analysis. */
    private static final ElliottDegree DEFAULT_DEGREE = ElliottDegree.PRIMARY;
    /** Default Fibonacci tolerance (0.25 = 25%) for phase validation. */
    private static final double DEFAULT_FIB_TOLERANCE = 0.25;

    /**
     * Main entry point for the Elliott Wave analysis demonstration.
     * <p>
     * Supports two modes of operation:
     * <ol>
     * <li><b>With arguments (5-6 args):</b> Loads data from an external data source
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
     * (case-insensitive)</li>
     * <li>Start epoch: Unix timestamp in seconds</li>
     * <li>End epoch: Unix timestamp in seconds (optional, defaults to current
     * time)</li>
     * </ol>
     *
     * @param args command-line arguments (optional). If 5-6 arguments are provided,
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

        ElliottDegree degree = parseDegreeFromArgs(args);
        new ElliottWaveAnalysis().analyze(series, degree, DEFAULT_FIB_TOLERANCE);
    }

    /**
     * Loads a bar series based on command-line arguments or defaults.
     * <p>
     * If 5-6 arguments are provided, loads from an external data source. Otherwise,
     * loads the default ossified dataset from classpath resources.
     *
     * @param args command-line arguments
     * @return the loaded bar series, or {@code null} if loading fails
     */
    private static BarSeries loadBarSeries(String[] args) {
        if (args.length >= 5) {
            return loadBarSeriesFromArgs(args);
        } else {
            return loadSeries(DEFAULT_OHLCV_RESOURCE);
        }
    }

    /**
     * Parses command-line arguments and loads a bar series from an external data
     * source.
     *
     * @param args command-line arguments (must have at least 5 elements)
     * @return the loaded bar series, or {@code null} if parsing or loading fails
     */
    private static BarSeries loadBarSeriesFromArgs(String[] args) {
        try {
            String dataSource = args[0];
            String ticker = args[1];
            String barDurationStr = args[2];
            String startEpochStr = args[4];
            String endEpochStr = args.length >= 6 ? args[5] : null;

            Duration barDuration = Duration.parse(barDurationStr);
            Instant startTime = Instant.ofEpochSecond(Long.parseLong(startEpochStr));
            Instant endTime = endEpochStr != null ? Instant.ofEpochSecond(Long.parseLong(endEpochStr)) : Instant.now();

            BarSeries series = loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime, endTime);
            if (series == null) {
                LOG.error("Failed to retrieve bar series from {} for ticker {} with duration {} from {} to {}",
                        dataSource, ticker, barDurationStr, startTime, endTime);
            }
            return series;
        } catch (Exception ex) {
            LOG.error("Error parsing arguments or loading series: {}", ex.getMessage(), ex);
            return null;
        }
    }

    /**
     * Parses the Elliott degree from command-line arguments, or returns the
     * default.
     *
     * @param args command-line arguments
     * @return the parsed Elliott degree, or {@link #DEFAULT_DEGREE} if not provided
     */
    private static ElliottDegree parseDegreeFromArgs(String[] args) {
        if (args.length >= 4) {
            try {
                return ElliottDegree.valueOf(args[3].toUpperCase());
            } catch (IllegalArgumentException ex) {
                LOG.warn("Invalid degree '{}', using default: {}", args[3], DEFAULT_DEGREE);
                return DEFAULT_DEGREE;
            }
        }
        return DEFAULT_DEGREE;
    }

    /**
     * Performs comprehensive Elliott Wave analysis on the provided bar series.
     * <p>
     * This method orchestrates the complete analysis workflow:
     * <ol>
     * <li>Sets up the Elliott Wave analysis framework (compressor and facade)</li>
     * <li>Retrieves all analysis indicators</li>
     * <li>Logs analysis results and scenario details</li>
     * <li>Generates chart visualizations</li>
     * </ol>
     * <p>
     * Charts are saved to {@code temp/charts/} directory and displayed if running
     * in a non-headless environment.
     *
     * @param series       the bar series to analyze
     * @param degree       the Elliott wave degree to use for swing detection
     * @param fibTolerance the Fibonacci tolerance (0.0-1.0) for phase validation.
     *                     Higher values allow more deviation from ideal Fibonacci
     *                     ratios. Default is 0.25 (25%).
     */
    public void analyze(BarSeries series, ElliottDegree degree, double fibTolerance) {
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

        logAnalysisResults(phaseIndicator, invalidationIndicator, channelIndicator, ratioIndicator, confluenceIndicator,
                swingMetadata, endIndex);
        logScenarioAnalysis(scenarioIndicator, endIndex);

        // Create chart indicators
        Indicator<Num> channelUpper = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.UPPER);
        Indicator<Num> channelLower = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.LOWER);
        Indicator<Num> channelMedian = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.MEDIAN);
        Indicator<Num> ratioValue = new RatioValueIndicator(series, ratioIndicator, "Elliott ratio value");
        Indicator<Num> swingCountAsNum = new IntegerAsNumIndicator(series, swingCount, "Swings (raw)");
        Indicator<Num> filteredSwingCountAsNum = new IntegerAsNumIndicator(series, filteredSwingCount,
                "Swings (compressed)");

        displayCharts(series, degree, scenarioIndicator, channelUpper, channelLower, channelMedian, ratioValue,
                swingCountAsNum, filteredSwingCountAsNum, confluenceIndicator, endIndex);
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
     * Logs the swing snapshot and latest analysis results (phase, ratio, channel,
     * confluence, invalidation).
     *
     * @param phaseIndicator        the phase indicator
     * @param invalidationIndicator the invalidation indicator
     * @param channelIndicator      the channel indicator
     * @param ratioIndicator        the ratio indicator
     * @param confluenceIndicator   the confluence indicator
     * @param swingMetadata         the swing metadata
     * @param endIndex              the index to evaluate (typically the last bar)
     */
    private static void logAnalysisResults(ElliottPhaseIndicator phaseIndicator,
            ElliottInvalidationIndicator invalidationIndicator, ElliottChannelIndicator channelIndicator,
            ElliottRatioIndicator ratioIndicator, ElliottConfluenceIndicator confluenceIndicator,
            ElliottSwingMetadata swingMetadata, int endIndex) {
        LOG.info("Elliott swing snapshot valid={}, swings={}, high={}, low={}", swingMetadata.isValid(),
                swingMetadata.size(), swingMetadata.highestPrice(), swingMetadata.lowestPrice());

        ElliottRatio latestRatio = ratioIndicator.getValue(endIndex);
        ElliottChannel latestChannel = channelIndicator.getValue(endIndex);
        LOG.info("Latest phase={} impulseConfirmed={} correctiveConfirmed={}", phaseIndicator.getValue(endIndex),
                phaseIndicator.isImpulseConfirmed(endIndex), phaseIndicator.isCorrectiveConfirmed(endIndex));
        LOG.info("Latest ratio type={} value={}", latestRatio.type(), latestRatio.value());
        LOG.info("Latest channel valid={} upper={} lower={} median={}", latestChannel.isValid(), latestChannel.upper(),
                latestChannel.lower(), latestChannel.median());
        LOG.info("Latest confluence score={} confluent={}", confluenceIndicator.getValue(endIndex),
                confluenceIndicator.isConfluent(endIndex));
        LOG.info("Latest invalidation={}", invalidationIndicator.getValue(endIndex));
    }

    /**
     * Logs detailed scenario analysis including primary scenario confidence scores
     * and alternative scenarios.
     *
     * @param scenarioIndicator the scenario indicator
     * @param endIndex          the index to evaluate (typically the last bar)
     */
    private static void logScenarioAnalysis(ElliottScenarioIndicator scenarioIndicator, int endIndex) {
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(endIndex);
        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", scenarioSet.summary());
        LOG.info("Strong consensus: {} | Consensus phase: {}", scenarioSet.hasStrongConsensus(),
                scenarioSet.consensus());

        if (scenarioSet.primary().isPresent()) {
            logPrimaryScenario(scenarioSet.primary().get());
        }

        List<ElliottScenario> alternatives = scenarioSet.alternatives();
        if (!alternatives.isEmpty()) {
            logAlternativeScenarios(alternatives);
        }
        LOG.info("======================================");
    }

    /**
     * Logs detailed information about the primary scenario.
     *
     * @param primary the primary scenario
     */
    private static void logPrimaryScenario(ElliottScenario primary) {
        ElliottConfidence confidence = primary.confidence();
        LOG.info("PRIMARY SCENARIO: {} ({})", primary.currentPhase(), primary.type());
        LOG.info("  Overall confidence: {}% ({})", String.format("%.1f", confidence.asPercentage()),
                confidence.isHighConfidence() ? "HIGH" : confidence.isLowConfidence() ? "LOW" : "MEDIUM");
        LOG.info("  Factor scores: Fibonacci={}% | Time={}% | Alternation={}% | Channel={}% | Completeness={}%",
                String.format("%.1f", confidence.fibonacciScore().doubleValue() * 100),
                String.format("%.1f", confidence.timeProportionScore().doubleValue() * 100),
                String.format("%.1f", confidence.alternationScore().doubleValue() * 100),
                String.format("%.1f", confidence.channelScore().doubleValue() * 100),
                String.format("%.1f", confidence.completenessScore().doubleValue() * 100));
        LOG.info("  Primary reason: {}", confidence.primaryReason());
        LOG.info("  Weakest factor: {}", confidence.weakestFactor());
        LOG.info("  Direction: {} | Invalidation: {} | Target: {}", primary.isBullish() ? "BULLISH" : "BEARISH",
                primary.invalidationPrice(), primary.primaryTarget());
    }

    /**
     * Logs information about alternative scenarios (up to 3).
     *
     * @param alternatives the list of alternative scenarios
     */
    private static void logAlternativeScenarios(List<ElliottScenario> alternatives) {
        LOG.info("ALTERNATIVE SCENARIOS ({}):", alternatives.size());
        for (int i = 0; i < Math.min(alternatives.size(), 3); i++) {
            ElliottScenario alt = alternatives.get(i);
            LOG.info("  {}. {} ({}) - {}% confidence", i + 1, alt.currentPhase(), alt.type(),
                    String.format("%.1f", alt.confidence().asPercentage()));
        }
    }

    /**
     * Orchestrates the display and saving of charts for all scenarios.
     *
     * @param series                  the bar series
     * @param degree                  the Elliott degree (for chart titles)
     * @param scenarioIndicator       the scenario indicator
     * @param channelUpper            indicator for upper channel boundary
     * @param channelLower            indicator for lower channel boundary
     * @param channelMedian           indicator for median channel boundary
     * @param ratioValue              indicator for Elliott ratio values
     * @param swingCountAsNum         indicator for raw swing count (as numeric)
     * @param filteredSwingCountAsNum indicator for filtered swing count (as
     *                                numeric)
     * @param confluenceIndicator     indicator for confluence scores
     * @param endIndex                the index to evaluate (typically the last bar)
     */
    private static void displayCharts(BarSeries series, ElliottDegree degree,
            ElliottScenarioIndicator scenarioIndicator, Indicator<Num> channelUpper, Indicator<Num> channelLower,
            Indicator<Num> channelMedian, Indicator<Num> ratioValue, Indicator<Num> swingCountAsNum,
            Indicator<Num> filteredSwingCountAsNum, ElliottConfluenceIndicator confluenceIndicator, int endIndex) {
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(endIndex);
        ChartWorkflow chartWorkflow = new ChartWorkflow();
        boolean isHeadless = GraphicsEnvironment.isHeadless();

        if (scenarioSet.primary().isPresent()) {
            displayPrimaryScenarioChart(series, degree, scenarioSet.primary().get(), channelUpper, channelLower,
                    channelMedian, ratioValue, swingCountAsNum, filteredSwingCountAsNum, confluenceIndicator,
                    chartWorkflow, isHeadless);
        }

        List<ElliottScenario> alternatives = scenarioSet.alternatives();
        displayAlternativeScenarioCharts(series, degree, alternatives, channelUpper, channelLower, channelMedian,
                ratioValue, swingCountAsNum, filteredSwingCountAsNum, confluenceIndicator, chartWorkflow, isHeadless);
    }

    /**
     * Displays and saves the chart for the primary scenario.
     *
     * @param series                  the bar series
     * @param degree                  the Elliott degree (for chart title)
     * @param primary                 the primary scenario
     * @param channelUpper            indicator for upper channel boundary
     * @param channelLower            indicator for lower channel boundary
     * @param channelMedian           indicator for median channel boundary
     * @param ratioValue              indicator for Elliott ratio values
     * @param swingCountAsNum         indicator for raw swing count (as numeric)
     * @param filteredSwingCountAsNum indicator for filtered swing count (as
     *                                numeric)
     * @param confluenceIndicator     indicator for confluence scores
     * @param chartWorkflow           the chart workflow instance
     * @param isHeadless              whether running in headless mode
     */
    private static void displayPrimaryScenarioChart(BarSeries series, ElliottDegree degree, ElliottScenario primary,
            Indicator<Num> channelUpper, Indicator<Num> channelLower, Indicator<Num> channelMedian,
            Indicator<Num> ratioValue, Indicator<Num> swingCountAsNum, Indicator<Num> filteredSwingCountAsNum,
            ElliottConfluenceIndicator confluenceIndicator, ChartWorkflow chartWorkflow, boolean isHeadless) {
        String primaryTitle = String.format("Elliott Wave (%s) - %s - PRIMARY: %s (%s) - %.1f%% confidence", degree,
                series.getName(), primary.currentPhase(), primary.type(), primary.confidence().asPercentage());
        String primaryWindowTitle = String.format("PRIMARY: %s (%s) - %.1f%% - %s", primary.currentPhase(),
                primary.type(), primary.confidence().asPercentage(), series.getName());

        BarSeriesLabelIndicator primaryWaveLabels = buildWaveLabelsFromScenario(series, primary);
        ChartPlan primaryPlan = buildChartPlan(chartWorkflow, series, channelUpper, channelLower, channelMedian,
                primaryWaveLabels, swingCountAsNum, filteredSwingCountAsNum, ratioValue, confluenceIndicator,
                primaryTitle);

        if (!isHeadless) {
            chartWorkflow.display(primaryPlan, primaryWindowTitle);
        }
        chartWorkflow.save(primaryPlan, "temp/charts", "elliott-wave-analysis-" + series.getName().toLowerCase() + "-"
                + degree.name().toLowerCase() + "-primary");
    }

    /**
     * Displays and saves charts for alternative scenarios.
     *
     * @param series                  the bar series
     * @param degree                  the Elliott degree (for chart titles)
     * @param alternatives            the list of alternative scenarios
     * @param channelUpper            indicator for upper channel boundary
     * @param channelLower            indicator for lower channel boundary
     * @param channelMedian           indicator for median channel boundary
     * @param ratioValue              indicator for Elliott ratio values
     * @param swingCountAsNum         indicator for raw swing count (as numeric)
     * @param filteredSwingCountAsNum indicator for filtered swing count (as
     *                                numeric)
     * @param confluenceIndicator     indicator for confluence scores
     * @param chartWorkflow           the chart workflow instance
     * @param isHeadless              whether running in headless mode
     */
    private static void displayAlternativeScenarioCharts(BarSeries series, ElliottDegree degree,
            List<ElliottScenario> alternatives, Indicator<Num> channelUpper, Indicator<Num> channelLower,
            Indicator<Num> channelMedian, Indicator<Num> ratioValue, Indicator<Num> swingCountAsNum,
            Indicator<Num> filteredSwingCountAsNum, ElliottConfluenceIndicator confluenceIndicator,
            ChartWorkflow chartWorkflow, boolean isHeadless) {
        for (int i = 0; i < alternatives.size(); i++) {
            ElliottScenario alt = alternatives.get(i);
            String altTitle = String.format("Elliott Wave (%s) - %s - ALTERNATIVE %d: %s (%s) - %.1f%% confidence",
                    degree, series.getName(), i + 1, alt.currentPhase(), alt.type(), alt.confidence().asPercentage());
            String altWindowTitle = String.format("ALTERNATIVE %d: %s (%s) - %.1f%% - %s", i + 1, alt.currentPhase(),
                    alt.type(), alt.confidence().asPercentage(), series.getName());

            BarSeriesLabelIndicator altWaveLabels = buildWaveLabelsFromScenario(series, alt);
            ChartPlan altPlan = buildChartPlan(chartWorkflow, series, channelUpper, channelLower, channelMedian,
                    altWaveLabels, swingCountAsNum, filteredSwingCountAsNum, ratioValue, confluenceIndicator, altTitle);

            if (!isHeadless) {
                chartWorkflow.display(altPlan, altWindowTitle);
            }
            chartWorkflow.save(altPlan, "temp/charts", "elliott-wave-analysis-" + series.getName().toLowerCase() + "-"
                    + degree.name().toLowerCase() + "-alternative-" + (i + 1));
        }
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
            LOG.error("Exception while loading series from {}: {}", dataSource, ex.getMessage(), ex);
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
     * Channel boundaries are color-coded:
     * <ul>
     * <li>Upper channel: Red (#F05454)</li>
     * <li>Lower channel: Teal (#26A69A)</li>
     * <li>Median channel: Light gray (semi-transparent)</li>
     * </ul>
     *
     * @param chartWorkflow           the chart workflow instance for building
     *                                charts
     * @param series                  the bar series to chart
     * @param channelUpper            indicator for upper channel boundary
     * @param channelLower            indicator for lower channel boundary
     * @param channelMedian           indicator for median channel boundary
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
    private static ChartPlan buildChartPlan(ChartWorkflow chartWorkflow, BarSeries series, Indicator<Num> channelUpper,
            Indicator<Num> channelLower, Indicator<Num> channelMedian, BarSeriesLabelIndicator waveLabels,
            Indicator<Num> swingCountAsNum, Indicator<Num> filteredSwingCountAsNum, Indicator<Num> ratioValue,
            Indicator<Num> confluenceIndicator, String title) {
        return chartWorkflow.builder()
                .withTitle(title)
                .withSeries(series)
                .withIndicatorOverlay(channelUpper)
                .withLineColor(new Color(0xF05454))
                .withLineWidth(1.4f)
                .withOpacity(0.85f)
                .withLabel("Elliott channel upper")
                .withIndicatorOverlay(channelLower)
                .withLineColor(new Color(0x26A69A))
                .withLineWidth(1.4f)
                .withOpacity(0.85f)
                .withLabel("Elliott channel lower")
                .withIndicatorOverlay(channelMedian)
                .withLineColor(Color.LIGHT_GRAY)
                .withLineWidth(1.2f)
                .withOpacity(0.55f)
                .withLabel("Elliott channel median")
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

    /**
     * Enumeration of Elliott channel boundary types.
     */
    private enum ChannelBoundary {
        /** Upper channel boundary (resistance level). */
        UPPER,
        /** Lower channel boundary (support level). */
        LOWER,
        /** Median channel boundary (midpoint between upper and lower). */
        MEDIAN
    }

    /**
     * Indicator that extracts a specific boundary value from an Elliott channel
     * indicator.
     * <p>
     * This wrapper indicator allows individual channel boundaries (upper, lower, or
     * median) to be used as separate indicators for charting purposes.
     */
    private static final class ChannelBoundaryIndicator extends CachedIndicator<Num> {

        private final ElliottChannelIndicator channelIndicator;
        private final ChannelBoundary boundary;

        /**
         * Creates a channel boundary indicator.
         *
         * @param series           the bar series
         * @param channelIndicator the Elliott channel indicator to extract boundaries
         *                         from
         * @param boundary         the boundary type to extract (UPPER, LOWER, or
         *                         MEDIAN)
         */
        private ChannelBoundaryIndicator(BarSeries series, ElliottChannelIndicator channelIndicator,
                ChannelBoundary boundary) {
            super(series);
            this.channelIndicator = Objects.requireNonNull(channelIndicator, "channelIndicator");
            this.boundary = Objects.requireNonNull(boundary, "boundary");
        }

        @Override
        protected Num calculate(int index) {
            ElliottChannel channel = channelIndicator.getValue(index);
            if (channel == null) {
                return getBarSeries().numFactory().numOf(Double.NaN);
            }
            return switch (boundary) {
            case UPPER -> channel.upper();
            case LOWER -> channel.lower();
            case MEDIAN -> channel.median();
            };
        }

        @Override
        public int getCountOfUnstableBars() {
            return channelIndicator.getCountOfUnstableBars();
        }
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
}
