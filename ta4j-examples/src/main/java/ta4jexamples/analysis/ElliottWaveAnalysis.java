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
import org.ta4j.core.indicators.elliott.ElliottFibonacciValidator;
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
import org.ta4j.core.indicators.elliott.ElliottSwingIndicator;
import org.ta4j.core.indicators.elliott.ElliottSwingMetadata;
import org.ta4j.core.indicators.elliott.ElliottWaveCountIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
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
 */
public class ElliottWaveAnalysis {

    private static final Logger LOG = LogManager.getLogger(ElliottWaveAnalysis.class);

    private static final String DEFAULT_OHLCV_RESOURCE = "Coinbase-BTC-USD-PT1D-20230616_20231011.json";
    private static final ElliottDegree DEFAULT_DEGREE = ElliottDegree.PRIMARY;
    private static final double DEFAULT_FIB_TOLERANCE = 0.25;

    public static void main(String[] args) {
        BarSeries series = null;
        ElliottDegree degree = DEFAULT_DEGREE;

        // If 5 or 6 args provided, use them to load from datasource
        if (args.length >= 5) {
            try {
                String dataSource = args[0];
                String ticker = args[1];
                String barDurationStr = args[2];
                String degreeStr = args[3];
                String startEpochStr = args[4];
                String endEpochStr = args.length >= 6 ? args[5] : null;

                // Parse bar duration
                Duration barDuration = Duration.parse(barDurationStr);

                // Parse start time
                long startEpochSeconds = Long.parseLong(startEpochStr);
                Instant startTime = Instant.ofEpochSecond(startEpochSeconds);

                // Parse end time (or use current time if not provided)
                Instant endTime = endEpochStr != null ? Instant.ofEpochSecond(Long.parseLong(endEpochStr))
                        : Instant.now();

                // Parse degree
                degree = ElliottDegree.valueOf(degreeStr.toUpperCase());

                // Load series from datasource
                series = loadSeriesFromDataSource(dataSource, ticker, barDuration, startTime, endTime);
                if (series == null) {
                    LOG.error("Failed to retrieve bar series from {} for ticker {} with duration {} from {} to {}",
                            dataSource, ticker, barDurationStr, startTime, endTime);
                    System.exit(1);
                    return; // Never reached, but satisfies compiler
                }
            } catch (Exception ex) {
                LOG.error("Error parsing arguments or loading series: {}", ex.getMessage(), ex);
                System.exit(1);
                return; // Never reached, but satisfies compiler
            }
        } else {
            // Use defaults
            series = loadSeries(DEFAULT_OHLCV_RESOURCE);
        }

        Objects.requireNonNull(series, "Bar series was null");
        if (series.isEmpty()) {
            LOG.error("Series is empty, nothing to analyse.");
            return;
        }
        new ElliottWaveAnalysis().analyze(series, degree, DEFAULT_FIB_TOLERANCE);
    }

    public void analyze(BarSeries series, ElliottDegree degree, double fibTolerance) {
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);

        ElliottSwingIndicator swingIndicator = ElliottSwingIndicator.zigZag(series, degree);
        ElliottFibonacciValidator validator = new ElliottFibonacciValidator(series.numFactory(),
                series.numFactory().numOf(fibTolerance));
        ElliottPhaseIndicator phaseIndicator = new ElliottPhaseIndicator(swingIndicator, validator);
        ElliottInvalidationIndicator invalidationIndicator = new ElliottInvalidationIndicator(phaseIndicator);

        ElliottChannelIndicator channelIndicator = new ElliottChannelIndicator(swingIndicator);
        ElliottRatioIndicator ratioIndicator = new ElliottRatioIndicator(swingIndicator);
        ElliottConfluenceIndicator confluenceIndicator = new ElliottConfluenceIndicator(closePrice, ratioIndicator,
                channelIndicator);

        ElliottWaveCountIndicator swingCount = new ElliottWaveCountIndicator(swingIndicator);
        ElliottSwingCompressor compressor = new ElliottSwingCompressor(
                closePrice.getValue(series.getEndIndex()).multipliedBy(series.numFactory().numOf(0.01)), 2);
        ElliottWaveCountIndicator filteredSwingCount = new ElliottWaveCountIndicator(swingIndicator, compressor);

        // Scenario-based analysis with confidence scoring
        ElliottScenarioIndicator scenarioIndicator = new ElliottScenarioIndicator(swingIndicator, channelIndicator);

        int endIndex = series.getEndIndex();
        ElliottSwingMetadata snapshot = ElliottSwingMetadata.of(swingIndicator.getValue(endIndex), series.numFactory());
        LOG.info("Elliott swing snapshot valid={}, swings={}, high={}, low={}", snapshot.isValid(), snapshot.size(),
                snapshot.highestPrice(), snapshot.lowestPrice());

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

        // Log scenario-based analysis with confidence percentages
        ElliottScenarioSet scenarioSet = scenarioIndicator.getValue(endIndex);
        LOG.info("=== Elliott Wave Scenario Analysis ===");
        LOG.info("Scenario summary: {}", scenarioSet.summary());
        LOG.info("Strong consensus: {} | Consensus phase: {}", scenarioSet.hasStrongConsensus(),
                scenarioSet.consensus());

        if (scenarioSet.primary().isPresent()) {
            ElliottScenario primary = scenarioSet.primary().get();
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

        // Log alternative scenarios
        List<ElliottScenario> alternatives = scenarioSet.alternatives();
        if (!alternatives.isEmpty()) {
            LOG.info("ALTERNATIVE SCENARIOS ({}):", alternatives.size());
            for (int i = 0; i < Math.min(alternatives.size(), 3); i++) {
                ElliottScenario alt = alternatives.get(i);
                LOG.info("  {}. {} ({}) - {}% confidence", i + 1, alt.currentPhase(), alt.type(),
                        String.format("%.1f", alt.confidence().asPercentage()));
            }
        }
        LOG.info("======================================");

        Indicator<Num> channelUpper = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.UPPER);
        Indicator<Num> channelLower = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.LOWER);
        Indicator<Num> channelMedian = new ChannelBoundaryIndicator(series, channelIndicator, ChannelBoundary.MEDIAN);
        Indicator<Num> ratioValue = new RatioValueIndicator(series, ratioIndicator, "Elliott ratio value");
        Indicator<Num> swingCountAsNum = new IntegerAsNumIndicator(series, swingCount, "Swings (raw)");
        Indicator<Num> filteredSwingCountAsNum = new IntegerAsNumIndicator(series, filteredSwingCount,
                "Swings (compressed)");

        ChartWorkflow chartWorkflow = new ChartWorkflow();
        boolean isHeadless = GraphicsEnvironment.isHeadless();

        // Display chart for primary scenario
        if (scenarioSet.primary().isPresent()) {
            ElliottScenario primary = scenarioSet.primary().get();
            String primaryTitle = String.format("Elliott Wave (%s) - %s - PRIMARY: %s (%s) - %.1f%% confidence", degree,
                    series.getName(), primary.currentPhase(), primary.type(), primary.confidence().asPercentage());
            String primaryWindowTitle = String.format("PRIMARY: %s (%s) - %.1f%% - %s", primary.currentPhase(),
                    primary.type(), primary.confidence().asPercentage(), series.getName());

            // Build scenario-specific wave labels from the scenario's swings
            BarSeriesLabelIndicator primaryWaveLabels = buildWaveLabelsFromScenario(series, primary);

            ChartPlan primaryPlan = buildChartPlan(chartWorkflow, series, degree, channelUpper, channelLower,
                    channelMedian, primaryWaveLabels, swingCountAsNum, filteredSwingCountAsNum, ratioValue,
                    confluenceIndicator, primaryTitle);

            if (!isHeadless) {
                chartWorkflow.display(primaryPlan, primaryWindowTitle);
            }
            chartWorkflow.save(primaryPlan, "temp/charts", "elliott-wave-analysis-" + series.getName().toLowerCase()
                    + "-" + degree.name().toLowerCase() + "-primary");
        }

        // Display charts for alternative scenarios
        for (int i = 0; i < alternatives.size(); i++) {
            ElliottScenario alt = alternatives.get(i);
            String altTitle = String.format("Elliott Wave (%s) - %s - ALTERNATIVE %d: %s (%s) - %.1f%% confidence",
                    degree, series.getName(), i + 1, alt.currentPhase(), alt.type(), alt.confidence().asPercentage());
            String altWindowTitle = String.format("ALTERNATIVE %d: %s (%s) - %.1f%% - %s", i + 1, alt.currentPhase(),
                    alt.type(), alt.confidence().asPercentage(), series.getName());

            // Build scenario-specific wave labels from the scenario's swings
            BarSeriesLabelIndicator altWaveLabels = buildWaveLabelsFromScenario(series, alt);

            ChartPlan altPlan = buildChartPlan(chartWorkflow, series, degree, channelUpper, channelLower, channelMedian,
                    altWaveLabels, swingCountAsNum, filteredSwingCountAsNum, ratioValue, confluenceIndicator, altTitle);

            if (!isHeadless) {
                chartWorkflow.display(altPlan, altWindowTitle);
            }
            chartWorkflow.save(altPlan, "temp/charts", "elliott-wave-analysis-" + series.getName().toLowerCase() + "-"
                    + degree.name().toLowerCase() + "-alternative-" + (i + 1));
        }

    }

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

    private static ChartPlan buildChartPlan(ChartWorkflow chartWorkflow, BarSeries series, ElliottDegree degree,
            Indicator<Num> channelUpper, Indicator<Num> channelLower, Indicator<Num> channelMedian,
            BarSeriesLabelIndicator waveLabels, Indicator<Num> swingCountAsNum, Indicator<Num> filteredSwingCountAsNum,
            Indicator<Num> ratioValue, Indicator<Num> confluenceIndicator, String title) {
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

    private static BarSeriesLabelIndicator buildWaveLabelsFromScenario(BarSeries series, ElliottScenario scenario) {
        List<ElliottSwing> swings = scenario.swings();
        if (swings.isEmpty()) {
            return new BarSeriesLabelIndicator(series, new ArrayList<>());
        }

        List<BarLabel> labels = new ArrayList<>();
        ScenarioType type = scenario.type();

        if (type.isImpulse()) {
            // For impulse patterns, label swings as 1, 2, 3, 4, 5
            if (!swings.isEmpty()) {
                ElliottSwing first = swings.get(0);
                labels.add(
                        new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
            }

            for (int i = 0; i < swings.size(); i++) {
                ElliottSwing swing = swings.get(i);
                labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                        placementForPivot(swing.isRising())));
            }
        } else if (type.isCorrective()) {
            // For corrective patterns, label swings as A, B, C
            if (!swings.isEmpty()) {
                ElliottSwing first = swings.get(0);
                labels.add(
                        new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
            }

            for (int i = 0; i < swings.size(); i++) {
                ElliottSwing swing = swings.get(i);
                String label = switch (i) {
                case 0 -> "A";
                case 1 -> "B";
                default -> "C";
                };
                labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), label, placementForPivot(swing.isRising())));
            }
        } else {
            // For unknown types, just label with indices
            if (!swings.isEmpty()) {
                ElliottSwing first = swings.get(0);
                labels.add(
                        new BarLabel(first.fromIndex(), first.fromPrice(), "", placementForPivot(!first.isRising())));
            }

            for (int i = 0; i < swings.size(); i++) {
                ElliottSwing swing = swings.get(i);
                labels.add(new BarLabel(swing.toIndex(), swing.toPrice(), String.valueOf(i + 1),
                        placementForPivot(swing.isRising())));
            }
        }

        return new BarSeriesLabelIndicator(series, labels);
    }

    private static LabelPlacement placementForPivot(boolean isHighPivot) {
        return isHighPivot ? LabelPlacement.ABOVE : LabelPlacement.BELOW;
    }

    private enum ChannelBoundary {
        UPPER, LOWER, MEDIAN
    }

    private static final class ChannelBoundaryIndicator extends CachedIndicator<Num> {

        private final ElliottChannelIndicator channelIndicator;
        private final ChannelBoundary boundary;

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

    private static final class RatioValueIndicator extends CachedIndicator<Num> {

        private final ElliottRatioIndicator ratioIndicator;
        private final String label;

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

    private static final class IntegerAsNumIndicator extends CachedIndicator<Num> {

        private final Indicator<Integer> delegate;
        private final String label;

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
