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
import org.ta4j.core.AnalysisCriterion;
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
 * <li>Trading record charts with optional indicators as subplots</li>
 * <li>Indicator-only charts with optional indicators as subplots</li>
 * <li>Dual-axis charts (automatically created when an analysis criterion is
 * specified)</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>
 * ChartHandle handle = chartMaker.builder()
 *         .withTradingRecord(series, strategyName, tradingRecord)
 *         .addIndicators(rsi, macd)
 *         .withAnalysisCriterion(series, tradingRecord, new MaximumDrawdownCriterion())
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
        TRADING_RECORD, INDICATORS
    }

    private final ChartMaker chartMaker;
    private final TradingChartFactory chartFactory;

    private ChartType chartType;
    private BarSeries series;
    private String strategyName;
    private TradingRecord tradingRecord;
    private final List<Indicator<Num>> additionalIndicators = new ArrayList<>();
    private AnalysisCriterion analysisCriterion;
    private String analysisCriterionLabel;
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
     * Configures the builder to create an indicator-only chart (no OHLC
     * candlesticks).
     *
     * @param series     the bar series
     * @param indicators the indicators to display as subplots
     * @return this builder for method chaining
     * @throws IllegalStateException if a different base chart type has already been
     *                               set
     * @since 0.19
     */
    @SafeVarargs
    public final ChartBuilder withIndicators(BarSeries series, Indicator<Num>... indicators) {
        validateBaseTypeNotSet();
        this.chartType = ChartType.INDICATORS;
        this.series = Objects.requireNonNull(series, "Series cannot be null");
        if (indicators == null) {
            throw new IllegalArgumentException("Indicators cannot be null");
        }
        if (indicators.length == 0) {
            throw new IllegalArgumentException("At least one indicator must be provided");
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
     * Adds an analysis criterion to visualize on the chart. This will create a
     * dual-axis chart with the base chart (price or indicators) on the left axis
     * and the analysis criterion on the right axis.
     *
     * <p>
     * Only one analysis criterion can be added per chart. The criterion will be
     * visualized as a time series showing its value at each bar index.
     * </p>
     *
     * <p>
     * If no label is provided, it will be inferred from the criterion class name by
     * removing the "Criterion" suffix. For example, {@code ExpectancyCriterion}
     * becomes "Expectancy".
     * </p>
     *
     * @param series        the bar series
     * @param tradingRecord the trading record to calculate the criterion from
     * @param criterion     the analysis criterion to visualize
     * @return this builder for method chaining
     * @throws IllegalStateException if an analysis criterion has already been set
     * @since 0.19
     */
    public ChartBuilder withAnalysisCriterion(BarSeries series, TradingRecord tradingRecord,
            AnalysisCriterion criterion) {
        return withAnalysisCriterion(series, tradingRecord, criterion, null);
    }

    /**
     * Adds an analysis criterion to visualize on the chart. This will create a
     * dual-axis chart with the base chart (price or indicators) on the left axis
     * and the analysis criterion on the right axis.
     *
     * <p>
     * Only one analysis criterion can be added per chart. The criterion will be
     * visualized as a time series showing its value at each bar index.
     * </p>
     *
     * <p>
     * If the label is null or empty, it will be inferred from the criterion class
     * name by removing the "Criterion" suffix. For example,
     * {@code ExpectancyCriterion} becomes "Expectancy".
     * </p>
     *
     * @param series        the bar series
     * @param tradingRecord the trading record to calculate the criterion from
     * @param criterion     the analysis criterion to visualize
     * @param label         the label for the criterion axis (optional, will be
     *                      inferred if null or empty)
     * @return this builder for method chaining
     * @throws IllegalStateException if an analysis criterion has already been set
     * @since 0.19
     */
    public ChartBuilder withAnalysisCriterion(BarSeries series, TradingRecord tradingRecord,
            AnalysisCriterion criterion, String label) {
        if (this.analysisCriterion != null) {
            throw new IllegalStateException(
                    "Analysis criterion already set. Only one analysis criterion can be configured per chart.");
        }
        this.series = Objects.requireNonNull(series, "Series cannot be null");
        this.tradingRecord = Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        this.analysisCriterion = Objects.requireNonNull(criterion, "Criterion cannot be null");

        // Infer label from class name if not provided
        if (label == null || label.trim().isEmpty()) {
            this.analysisCriterionLabel = inferLabelFromCriterion(criterion);
        } else {
            this.analysisCriterionLabel = label;
        }
        return this;
    }

    /**
     * Infers a label from the analysis criterion class name by removing the
     * "Criterion" suffix.
     *
     * @param criterion the analysis criterion
     * @return the inferred label
     */
    private String inferLabelFromCriterion(AnalysisCriterion criterion) {
        String className = criterion.getClass().getSimpleName();
        if (className.endsWith("Criterion")) {
            return className.substring(0, className.length() - "Criterion".length());
        }
        return className;
    }

    /**
     * Adds additional indicators to the chart as subplots. Can only be used with
     * trading record charts.
     *
     * @param indicators the indicators to add as subplots
     * @return this builder for method chaining
     * @throws IllegalStateException if called on an indicator-only chart
     * @since 0.19
     */
    @SafeVarargs
    public final ChartBuilder addIndicators(Indicator<Num>... indicators) {
        if (chartType == ChartType.INDICATORS) {
            throw new IllegalStateException(
                    "Cannot add indicators to an indicator-only chart. Use withIndicators(series, ...) to create the base chart.");
        }
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
                    "No chart type configured. Call withTradingRecord() or withIndicators(series, ...) first.");
        }

        if (analysisCriterion != null && tradingRecord == null) {
            throw new IllegalStateException(
                    "Analysis criterion requires a trading record. Call withAnalysisCriterion() after withTradingRecord().");
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
        case INDICATORS -> buildIndicatorsChart();
        };
    }

    @SuppressWarnings("unchecked")
    private JFreeChart buildTradingRecordChart() {
        // If analysis criterion is specified, create dual-axis chart
        if (analysisCriterion != null) {
            AnalysisCriterionIndicator criterionIndicator = new AnalysisCriterionIndicator(series, analysisCriterion,
                    tradingRecord);
            Indicator<Num> closePrice = new org.ta4j.core.indicators.helpers.ClosePriceIndicator(series);

            Indicator<Num>[] indicatorsArray = additionalIndicators.isEmpty() ? null
                    : additionalIndicators.toArray(new Indicator[0]);

            String effectiveTitle = customTitle != null && !customTitle.trim().isEmpty() ? customTitle : null;
            JFreeChart chart = chartFactory.createDualAxisChartWithAnalysisCriterion(series, strategyName,
                    tradingRecord, closePrice, "Price", criterionIndicator, analysisCriterionLabel, indicatorsArray,
                    effectiveTitle);

            return chart;
        }

        // Otherwise, create standard trading record chart
        Indicator<Num>[] indicatorsArray = additionalIndicators.isEmpty() ? null
                : additionalIndicators.toArray(new Indicator[0]);

        JFreeChart chart;
        if (indicatorsArray != null && indicatorsArray.length > 0) {
            chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord, indicatorsArray);
        } else {
            chart = chartFactory.createTradingRecordChart(series, strategyName, tradingRecord);
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

    private JFreeChart buildIndicatorsChart() {
        @SuppressWarnings("unchecked")
        Indicator<Num>[] indicatorsArray = additionalIndicators.toArray(new Indicator[0]);
        JFreeChart chart = chartFactory.createIndicatorChart(series, indicatorsArray);

        // If analysis criterion is specified, convert to dual-axis
        if (analysisCriterion != null) {
            AnalysisCriterionIndicator criterionIndicator = new AnalysisCriterionIndicator(series, analysisCriterion,
                    tradingRecord);
            chart = chartFactory.addAnalysisCriterionToChart(chart, series, criterionIndicator, analysisCriterionLabel);
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
}
