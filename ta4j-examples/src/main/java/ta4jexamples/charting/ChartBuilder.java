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

import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.num.Num;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Builder for creating charts with a fluent API.
 *
 * <p>
 * This builder supports creating various chart types and combinations:
 * </p>
 * <ul>
 * <li>Trading record charts with optional indicators and analysis overlays</li>
 * <li>Analysis charts with optional indicators</li>
 * <li>Dual-axis charts with optional additional indicators</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * ChartHandle handle = chartMaker.builder()
 *         .withTradingRecord(series, strategyName, tradingRecord)
 *         .withIndicators(rsi, macd)
 *         .withAnalysis(AnalysisType.MOVING_AVERAGE_20)
 *         .withTitle("My Chart")
 *         .build()
 *         .display()
 *         .save("/path/to/save", "my-chart");
 * </pre>
 *
 * @since 0.19
 */
public final class ChartBuilder {

    private enum ChartType {
        TRADING_RECORD, ANALYSIS, DUAL_AXIS
    }

    private final ChartMaker chartMaker;
    private final TradingChartFactory chartFactory;

    private ChartType chartType;
    private BarSeries series;
    private String strategyName;
    private TradingRecord tradingRecord;
    private Indicator<Num> primaryIndicator;
    private String primaryLabel;
    private Indicator<Num> secondaryIndicator;
    private String secondaryLabel;
    private final List<Indicator<Num>> additionalIndicators = new ArrayList<>();
    private final List<AnalysisType> analysisTypes = new ArrayList<>();
    private String customTitle;

    ChartBuilder(ChartMaker chartMaker, TradingChartFactory chartFactory) {
        this.chartMaker = Objects.requireNonNull(chartMaker, "Chart maker cannot be null");
        this.chartFactory = Objects.requireNonNull(chartFactory, "Chart factory cannot be null");
    }

    /**
     * Configures the builder to create a trading record chart.
     *
     * @param series        the bar series
     * @param strategyName  the strategy name
     * @param tradingRecord the trading record
     * @return this builder for method chaining
     * @throws IllegalStateException if a different base chart type has already been
     *                               set
     * @since 0.19
     */
    public ChartBuilder withTradingRecord(BarSeries series, String strategyName, TradingRecord tradingRecord) {
        validateBaseTypeNotSet();
        this.chartType = ChartType.TRADING_RECORD;
        this.series = Objects.requireNonNull(series, "Series cannot be null");
        this.strategyName = Objects.requireNonNull(strategyName, "Strategy name cannot be null");
        this.tradingRecord = Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        return this;
    }

    /**
     * Configures the builder to create an analysis chart.
     *
     * @param series        the bar series
     * @param analysisTypes the analysis types to overlay
     * @return this builder for method chaining
     * @throws IllegalStateException if a different base chart type has already been
     *                               set
     * @since 0.19
     */
    public ChartBuilder withAnalysis(BarSeries series, AnalysisType... analysisTypes) {
        validateBaseTypeNotSet();
        this.chartType = ChartType.ANALYSIS;
        this.series = Objects.requireNonNull(series, "Series cannot be null");
        if (analysisTypes == null) {
            throw new IllegalArgumentException("Analysis types cannot be null");
        }
        for (AnalysisType type : analysisTypes) {
            if (type == null) {
                throw new IllegalArgumentException("Analysis types cannot contain null values");
            }
        }
        this.analysisTypes.addAll(Arrays.asList(analysisTypes));
        return this;
    }

    /**
     * Configures the builder to create a dual-axis chart.
     *
     * @param series             the bar series
     * @param primaryIndicator   the primary indicator (left axis)
     * @param primaryLabel       the label for the primary axis
     * @param secondaryIndicator the secondary indicator (right axis)
     * @param secondaryLabel     the label for the secondary axis
     * @return this builder for method chaining
     * @throws IllegalStateException if a different base chart type has already been
     *                               set
     * @since 0.19
     */
    public ChartBuilder withDualAxis(BarSeries series, Indicator<Num> primaryIndicator, String primaryLabel,
            Indicator<Num> secondaryIndicator, String secondaryLabel) {
        validateBaseTypeNotSet();
        this.chartType = ChartType.DUAL_AXIS;
        this.series = Objects.requireNonNull(series, "Series cannot be null");
        this.primaryIndicator = Objects.requireNonNull(primaryIndicator, "Primary indicator cannot be null");
        this.primaryLabel = Objects.requireNonNull(primaryLabel, "Primary label cannot be null");
        this.secondaryIndicator = Objects.requireNonNull(secondaryIndicator, "Secondary indicator cannot be null");
        this.secondaryLabel = Objects.requireNonNull(secondaryLabel, "Secondary label cannot be null");
        return this;
    }

    /**
     * Adds analysis overlays to the chart. Can be used with trading record or
     * analysis charts.
     *
     * @param analysisTypes the analysis types to add as overlays
     * @return this builder for method chaining
     * @since 0.19
     */
    public ChartBuilder addAnalysis(AnalysisType... analysisTypes) {
        if (analysisTypes == null) {
            throw new IllegalArgumentException("Analysis types cannot be null");
        }
        for (AnalysisType type : analysisTypes) {
            if (type == null) {
                throw new IllegalArgumentException("Analysis types cannot contain null values");
            }
        }
        this.analysisTypes.addAll(Arrays.asList(analysisTypes));
        return this;
    }

    /**
     * Adds indicators to the chart. For OHLC-based charts (trading record,
     * analysis), indicators are added as subplots. For dual-axis charts, indicators
     * are added as additional series.
     *
     * @param indicators the indicators to add
     * @return this builder for method chaining
     * @since 0.19
     */
    @SafeVarargs
    public final ChartBuilder withIndicators(Indicator<Num>... indicators) {
        if (indicators == null) {
            throw new IllegalArgumentException("Indicators cannot be null");
        }
        for (Indicator<Num> indicator : indicators) {
            if (indicator == null) {
                throw new IllegalArgumentException("Indicators cannot contain null values");
            }
        }
        this.additionalIndicators.addAll(Arrays.asList(indicators));
        return this;
    }

    /**
     * Sets a custom title for the chart. If not set, a title will be
     * auto-generated.
     *
     * @param title the custom chart title
     * @return this builder for method chaining
     * @since 0.19
     */
    public ChartBuilder withTitle(String title) {
        this.customTitle = title;
        return this;
    }

    /**
     * Builds the chart based on the configured options and returns a
     * {@link ChartHandle} for performing actions.
     *
     * @return a handle for the built chart
     * @throws IllegalStateException if no base chart type has been configured
     * @since 0.19
     */
    public ChartHandle build() {
        if (chartType == null) {
            throw new IllegalStateException(
                    "No chart type configured. Call withTradingRecord(), withAnalysis(), or withDualAxis() first.");
        }

        JFreeChart chart = buildChart();
        return new ChartHandle(chart, series, chartMaker);
    }

    private void validateBaseTypeNotSet() {
        if (chartType != null) {
            throw new IllegalStateException(
                    "Chart type already set to " + chartType + ". Only one base chart type can be configured.");
        }
    }

    private JFreeChart buildChart() {
        return switch (chartType) {
        case TRADING_RECORD -> buildTradingRecordChart();
        case ANALYSIS -> buildAnalysisChart();
        case DUAL_AXIS -> buildDualAxisChart();
        };
    }

    @SuppressWarnings("unchecked")
    private JFreeChart buildTradingRecordChart() {
        Indicator<Num>[] indicatorsArray = additionalIndicators.isEmpty() ? null
                : additionalIndicators.toArray(new Indicator[0]);

        JFreeChart chart;
        if (indicatorsArray != null && indicatorsArray.length > 0) {
            chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord, indicatorsArray);
        } else {
            chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord);
        }

        // Add analysis overlays if specified
        if (!analysisTypes.isEmpty()) {
            chartFactory.addAnalysisToChart(chart, series, analysisTypes.toArray(new AnalysisType[0]));
        }

        // Apply custom title if specified
        if (customTitle != null && !customTitle.trim().isEmpty()) {
            chart.setTitle(customTitle);
            if (chart.getTitle() != null) {
                chart.getTitle().setPaint(java.awt.Color.LIGHT_GRAY);
            }
        }

        return chart;
    }

    private JFreeChart buildAnalysisChart() {
        AnalysisType[] analysisArray = analysisTypes.isEmpty() ? new AnalysisType[0]
                : analysisTypes.toArray(new AnalysisType[0]);
        JFreeChart chart = chartFactory.createAnalysisChart(series, analysisArray);

        // Add indicators as subplots if specified
        if (!additionalIndicators.isEmpty()) {
            @SuppressWarnings("unchecked")
            Indicator<Num>[] indicatorsArray = additionalIndicators.toArray(new Indicator[0]);
            chart = chartFactory.addIndicatorsToChart(chart, series, indicatorsArray);
        }

        // Apply custom title if specified
        if (customTitle != null && !customTitle.trim().isEmpty()) {
            chart.setTitle(customTitle);
            if (chart.getTitle() != null) {
                chart.getTitle().setPaint(java.awt.Color.LIGHT_GRAY);
            }
        }

        return chart;
    }

    private JFreeChart buildDualAxisChart() {
        String effectiveTitle = customTitle != null && !customTitle.trim().isEmpty() ? customTitle : null;
        JFreeChart chart = chartFactory.createDualAxisChart(series, primaryIndicator, primaryLabel, secondaryIndicator,
                secondaryLabel, effectiveTitle);

        // Add additional indicators as series if specified
        if (!additionalIndicators.isEmpty()) {
            @SuppressWarnings("unchecked")
            Indicator<Num>[] indicatorsArray = additionalIndicators.toArray(new Indicator[0]);
            chartFactory.addIndicatorsToDualAxisChart(chart, series, indicatorsArray);
        }

        return chart;
    }
}
