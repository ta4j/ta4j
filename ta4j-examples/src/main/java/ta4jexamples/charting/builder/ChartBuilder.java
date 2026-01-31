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
package ta4jexamples.charting.builder;

import java.util.Collections;
import java.util.ArrayList;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.IdentityHashMap;
import java.util.Optional;
import java.util.Objects;
import java.awt.Color;
import java.util.List;
import java.util.Set;

import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.PriceChannel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.TradingRecord;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.Trade;
import org.ta4j.core.Bar;

import ta4jexamples.charting.compose.TradingChartFactory;
import ta4jexamples.charting.AnalysisCriterionIndicator;
import ta4jexamples.charting.ChannelBoundaryIndicator;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Fluent chart builder that mirrors the Java Stream API: users select a base
 * chart, optionally attach overlays/sub-charts, and then invoke a terminal
 * operation ({@link TerminalStage#display()},
 * {@link TerminalStage#save(String)}, {@link TerminalStage#toChart()}).
 *
 * <p>
 * The fluent chain enforces valid transitions via stage interfaces. Calling
 * {@link #withSeries(BarSeries)} or {@link #withIndicator(Indicator)} creates
 * the base chart. From there the returned {@link ChartStage} allows overlays or
 * nested sub-charts to be added. Terminal methods consume the builder to avoid
 * accidental re-use (matching the Stream contract).
 * </p>
 *
 * @since 0.20
 */
public final class ChartBuilder {

    private static final Logger LOG = LogManager.getLogger(ChartBuilder.class);
    private static final Color DEFAULT_CHANNEL_LINE_COLOR = new Color(0x8FA3AD);
    private static final float DEFAULT_CHANNEL_LINE_OPACITY = 0.5f;
    private static final float DEFAULT_CHANNEL_MEDIAN_OPACITY = 0.35f;
    private static final float DEFAULT_CHANNEL_MEDIAN_WIDTH = 0.9f;
    private static final float DEFAULT_CHANNEL_LINE_WIDTH = 1.2f;
    private static final float DEFAULT_CHANNEL_FILL_OPACITY = 0.25f;
    private static final float[] DEFAULT_CHANNEL_MEDIAN_DASH = { 4.5f, 4.5f };

    private final ChartWorkflow chartWorkflow;
    private final TradingChartFactory chartFactory;
    private final OverlayColorPalette colorPalette = new OverlayColorPalette();
    private final List<PlotContext> plots = new ArrayList<>();

    private boolean consumed;
    private String customTitle;
    private BarSeries domainSeries;
    private TimeAxisMode timeAxisMode = TimeAxisMode.REAL_TIME;

    public ChartBuilder(ChartWorkflow chartWorkflow, TradingChartFactory chartFactory) {
        this.chartWorkflow = Objects.requireNonNull(chartWorkflow, "Chart workflow cannot be null");
        this.chartFactory = Objects.requireNonNull(chartFactory, "Chart factory cannot be null");
    }

    /**
     * Sets a custom title before any base chart is configured.
     *
     * @param title the desired title
     * @return this builder
     */
    public ChartBuilder withTitle(String title) {
        this.customTitle = title;
        return this;
    }

    /**
     * Sets the time axis mode for the chart domain axis.
     *
     * @param timeAxisMode the time axis mode to apply
     * @return this builder
     * @since 0.23
     */
    public ChartBuilder withTimeAxisMode(TimeAxisMode timeAxisMode) {
        this.timeAxisMode = Objects.requireNonNull(timeAxisMode, "Time axis mode cannot be null");
        return this;
    }

    /**
     * Starts a candlestick (OHLCV) chart.
     *
     * @param series the bar series backing the chart
     * @return a chart stage that can accept overlays or terminal operations
     */
    public ChartStage withSeries(BarSeries series) {
        Objects.requireNonNull(series, "Series cannot be null");
        validateSeriesHasBars(series);
        ensureBaseNotConfigured();
        PlotContext context = PlotContext.candlestick(series);
        plots.add(context);
        domainSeries = series;
        return new PlotStageImpl(context);
    }

    /**
     * Starts a line chart using the provided indicator as the base.
     *
     * @param indicator the indicator representing the base line
     * @return a chart stage that can accept overlays or terminal operations
     */
    public ChartStage withIndicator(Indicator<Num> indicator) {
        Objects.requireNonNull(indicator, "Indicator cannot be null");
        BarSeries series = requireIndicatorSeries(indicator);
        ensureBaseNotConfigured();
        PlotContext context = PlotContext.indicator(series, indicator);
        plots.add(context);
        domainSeries = series;
        return new PlotStageImpl(context);
    }

    private void ensureBaseNotConfigured() {
        if (!plots.isEmpty()) {
            throw new IllegalStateException("A base chart has already been configured for this builder instance.");
        }
    }

    private void validateReadyForTerminal() {
        if (plots.isEmpty()) {
            throw new IllegalStateException("No chart configured. Call withSeries(...) or withIndicator(...) first.");
        }
        if (consumed) {
            throw new IllegalStateException("This builder has already been consumed by a terminal operation.");
        }
        validateChannelOverlays();
    }

    private void markConsumed() {
        this.consumed = true;
    }

    private void validateSeriesHasBars(BarSeries series) {
        if (series.getBarCount() == 0) {
            throw new IllegalArgumentException("Series must contain at least one bar");
        }
    }

    private BarSeries requireIndicatorSeries(Indicator<?> indicator) {
        BarSeries series = indicator.getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Indicator " + indicator + " is not attached to a BarSeries");
        }
        validateSeriesHasBars(series);
        return series;
    }

    private void validateChannelOverlays() {
        for (PlotContext plot : plots) {
            Set<Indicator<?>> channelIndicators = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
            for (ChannelOverlayContext channelOverlay : plot.channelOverlays) {
                channelIndicators.add(channelOverlay.upperIndicator);
                channelIndicators.add(channelOverlay.lowerIndicator);
                if (channelOverlay.medianIndicator != null) {
                    channelIndicators.add(channelOverlay.medianIndicator);
                }
            }
            EnumSet<PriceChannel.Boundary> boundaries = EnumSet.noneOf(PriceChannel.Boundary.class);
            boolean hasChannelBoundary = false;
            for (OverlayContext overlay : plot.overlays) {
                Indicator<Num> indicator = overlay.indicator;
                if (indicator instanceof ChannelBoundaryIndicator boundaryIndicator) {
                    if (channelIndicators.contains(indicator)) {
                        continue;
                    }
                    hasChannelBoundary = true;
                    boundaries.add(boundaryIndicator.boundary());
                }
            }
            if (hasChannelBoundary && (!boundaries.contains(PriceChannel.Boundary.UPPER)
                    || !boundaries.contains(PriceChannel.Boundary.LOWER))) {
                throw new IllegalStateException(
                        "Channel overlays require both upper and lower boundaries before charting.");
            }
        }
    }

    private StyledOverlayStage addIndicatorOverlay(PlotContext context, Indicator<Num> indicator, OverlayType type) {
        return addIndicatorOverlay(context, indicator, type, false);
    }

    private StyledOverlayStage addChannelBoundaryOverlay(PlotContext context, Indicator<Num> indicator) {
        StyledOverlayStage stage = addIndicatorOverlay(context, indicator, OverlayType.INDICATOR);
        applyChannelBoundaryDefaults(stage, indicator);
        return stage;
    }

    private StyledChannelStage addChannelOverlay(PlotContext context, Indicator<Num> upper, Indicator<Num> median,
            Indicator<Num> lower) {
        Objects.requireNonNull(upper, "Upper boundary indicator cannot be null");
        Objects.requireNonNull(lower, "Lower boundary indicator cannot be null");
        BarSeries upperSeries = requireIndicatorSeries(upper);
        BarSeries lowerSeries = requireIndicatorSeries(lower);
        if (!Objects.equals(upperSeries, lowerSeries)) {
            throw new IllegalArgumentException("Channel boundaries must be attached to the same BarSeries");
        }
        if (median != null && !Objects.equals(upperSeries, requireIndicatorSeries(median))) {
            throw new IllegalArgumentException("Channel median must be attached to the same BarSeries");
        }

        AxisRange combinedRange = AxisRange.forIndicator(upper).merge(AxisRange.forIndicator(lower));
        AxisSlot slot = assignAxis(context, combinedRange, "channel", false);
        if (slot == null) {
            LOG.warn("Skipping channel overlay because its range does not align with existing axes on {} chart.",
                    context.type);
            return new StyledChannelStageImpl(context, null);
        }

        OverlayStyle lineStyle = OverlayStyle.defaultStyle(DEFAULT_CHANNEL_LINE_COLOR);
        lineStyle.setLineWidth(DEFAULT_CHANNEL_LINE_WIDTH);
        lineStyle.setOpacity(DEFAULT_CHANNEL_LINE_OPACITY);

        OverlayStyle medianStyle = OverlayStyle.defaultStyle(DEFAULT_CHANNEL_LINE_COLOR);
        medianStyle.setLineWidth(DEFAULT_CHANNEL_MEDIAN_WIDTH);
        medianStyle.setOpacity(DEFAULT_CHANNEL_MEDIAN_OPACITY);
        medianStyle.setDashPattern(DEFAULT_CHANNEL_MEDIAN_DASH);
        ChannelFillStyle fillStyle = ChannelFillStyle.defaultStyle(DEFAULT_CHANNEL_LINE_COLOR,
                DEFAULT_CHANNEL_FILL_OPACITY);

        OverlayContext upperOverlay = OverlayContext.indicator(OverlayType.INDICATOR, upper, slot, lineStyle, null);
        OverlayContext lowerOverlay = OverlayContext.indicator(OverlayType.INDICATOR, lower, slot, lineStyle, null);
        OverlayContext medianOverlay = median != null
                ? OverlayContext.indicator(OverlayType.INDICATOR, median, slot, medianStyle, null)
                : null;

        context.overlays.add(upperOverlay);
        if (medianOverlay != null) {
            context.overlays.add(medianOverlay);
        }
        context.overlays.add(lowerOverlay);

        ChannelOverlayContext channelContext = new ChannelOverlayContext(upper, lower, median, slot, lineStyle,
                medianStyle, fillStyle);
        context.channelOverlays.add(channelContext);
        return new StyledChannelStageImpl(context, channelContext);
    }

    private StyledOverlayStage addIndicatorOverlay(PlotContext context, Indicator<Num> indicator, OverlayType type,
            boolean preferSecondaryAxis) {
        Objects.requireNonNull(indicator, "Indicator cannot be null");
        AxisSlot slot = assignAxis(context, AxisRange.forIndicator(indicator), indicator.toString(),
                preferSecondaryAxis);
        if (slot == null) {
            LOG.warn("Skipping {} overlay {} because it does not align with existing axes on {} chart.", type,
                    indicator, context.type);
            return new StyledOverlayStageImpl(context, null);
        }
        OverlayContext overlay = OverlayContext.indicator(type, indicator, slot, colorPalette.nextColor(), null);
        context.overlays.add(overlay);
        return new StyledOverlayStageImpl(context, overlay);
    }

    private void applyChannelBoundaryDefaults(StyledOverlayStage stage, Indicator<Num> indicator) {
        if (!(indicator instanceof ChannelBoundaryIndicator boundaryIndicator)) {
            return;
        }
        if (stage instanceof StyledOverlayStageImpl styledStage && styledStage.overlay == null) {
            return;
        }
        switch (boundaryIndicator.boundary()) {
        case UPPER -> stage.withLineColor(DEFAULT_CHANNEL_LINE_COLOR)
                .withLineWidth(DEFAULT_CHANNEL_LINE_WIDTH)
                .withOpacity(DEFAULT_CHANNEL_LINE_OPACITY);
        case LOWER -> stage.withLineColor(DEFAULT_CHANNEL_LINE_COLOR)
                .withLineWidth(DEFAULT_CHANNEL_LINE_WIDTH)
                .withOpacity(DEFAULT_CHANNEL_LINE_OPACITY);
        case MEDIAN -> {
            stage.withLineColor(DEFAULT_CHANNEL_LINE_COLOR)
                    .withLineWidth(DEFAULT_CHANNEL_MEDIAN_WIDTH)
                    .withOpacity(DEFAULT_CHANNEL_MEDIAN_OPACITY);
            if (stage instanceof StyledOverlayStageImpl styledStage) {
                styledStage.overlay.style.setDashPattern(DEFAULT_CHANNEL_MEDIAN_DASH);
            }
        }
        }
    }

    private ChartStage addTradingRecordOverlay(PlotContext context, TradingRecord tradingRecord) {
        Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        AxisSlot slot = assignAxis(context, AxisRange.forTradingRecord(tradingRecord), "trading-record", false);
        if (slot == null || slot == AxisSlot.SECONDARY) {
            // Trading markers should stay on the primary axis. If the range doesn't match
            // simply skip the overlay.
            LOG.warn(
                    "Skipping trading record overlay because its price range does not align with the existing axes on {} chart.",
                    context.type);
            return new PlotStageImpl(context);
        }
        OverlayContext overlay = OverlayContext.tradingRecord(tradingRecord);
        context.overlays.add(overlay);
        return new PlotStageImpl(context);
    }

    private StyledOverlayStage addAnalysisOverlay(PlotContext context, AnalysisCriterion criterion,
            TradingRecord tradingRecord) {
        Objects.requireNonNull(criterion, "Analysis criterion cannot be null");
        Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        BarSeries series = ensureDomainSeries();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(series, criterion, tradingRecord);
        return addIndicatorOverlay(context, indicator, OverlayType.ANALYSIS_CRITERION, true);
    }

    private StyledMarkerStage addHorizontalMarker(PlotContext context, double yValue) {
        HorizontalMarkerContext marker = HorizontalMarkerContext.create(yValue, colorPalette.nextColor());
        context.horizontalMarkers.add(marker);
        return new StyledMarkerStageImpl(context, marker);
    }

    private ChartStage addIndicatorSubChart(Indicator<Num> indicator) {
        BarSeries series = requireIndicatorSeries(indicator);
        PlotContext context = PlotContext.indicator(series, indicator);
        plots.add(context);
        return new PlotStageImpl(context);
    }

    private ChartStage addAnalysisSubChart(AnalysisCriterion criterion, TradingRecord tradingRecord) {
        Objects.requireNonNull(criterion, "Analysis criterion cannot be null");
        Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        BarSeries series = ensureDomainSeries();
        AnalysisCriterionIndicator indicator = new AnalysisCriterionIndicator(series, criterion, tradingRecord);
        PlotContext context = PlotContext.indicator(series, indicator);
        plots.add(context);
        return new PlotStageImpl(context);
    }

    private ChartStage addTradingRecordSubChart(TradingRecord tradingRecord) {
        Objects.requireNonNull(tradingRecord, "Trading record cannot be null");
        BarSeries series = ensureDomainSeries();
        PlotContext context = PlotContext.trading(series, tradingRecord);
        plots.add(context);
        return new PlotStageImpl(context);
    }

    private AxisSlot assignAxis(PlotContext context, AxisRange candidateRange, String overlayName,
            boolean preferSecondary) {
        AxisAssignment assignment = context.axisModel.assign(candidateRange, preferSecondary);
        if (assignment == AxisAssignment.REJECTED) {
            return null;
        }
        if (assignment == AxisAssignment.SECONDARY && context.type == PlotType.TRADING_RECORD) {
            // Trading record charts need both price axes available for price overlays to
            // keep markers meaningful.
            LOG.warn("Rejecting overlay {} because trading record charts reserve the secondary axis for analysis.",
                    overlayName);
            return null;
        }
        return assignment == AxisAssignment.PRIMARY ? AxisSlot.PRIMARY : AxisSlot.SECONDARY;
    }

    private BarSeries ensureDomainSeries() {
        if (domainSeries == null) {
            throw new IllegalStateException(
                    "No shared BarSeries available. Configure a base chart with a BarSeries before adding this element.");
        }
        return domainSeries;
    }

    private ChartDefinition buildDefinition() {
        PlotDefinition base = PlotDefinition.fromContext(plots.get(0));
        List<PlotDefinition> subplots;
        if (plots.size() > 1) {
            List<PlotDefinition> defs = new ArrayList<>();
            for (int i = 1; i < plots.size(); i++) {
                defs.add(PlotDefinition.fromContext(plots.get(i)));
            }
            subplots = Collections.unmodifiableList(defs);
        } else {
            subplots = Collections.emptyList();
        }
        ChartDefinitionMetadata metadata = new ChartDefinitionMetadata(
                Objects.requireNonNull(domainSeries, "Domain series cannot be null"), customTitle, timeAxisMode);
        return new ChartDefinition(base, subplots, metadata);
    }

    private ChartPlan createPlan() {
        return new ChartPlan(buildDefinition(), primarySeries());
    }

    private ChartPlan planForTerminal() {
        validateReadyForTerminal();
        ChartPlan plan = createPlan();
        markConsumed();
        return plan;
    }

    private BarSeries primarySeries() {
        return plots.get(0).series;
    }

    private void displayInternal(String windowTitle) {
        ChartPlan plan = planForTerminal();
        if (windowTitle == null) {
            chartWorkflow.display(plan);
        } else {
            chartWorkflow.display(plan, windowTitle);
        }
    }

    private Optional<Path> saveInternal(String directory, String filename) {
        ChartPlan plan = planForTerminal();
        return chartWorkflow.save(plan, directory, filename);
    }

    private Optional<Path> saveInternal(Path directory, String filename) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        return saveInternal(directory.toString(), filename);
    }

    private Optional<Path> saveInternal(String filename) {
        ChartPlan plan = planForTerminal();
        return chartWorkflow.save(plan, filename);
    }

    private Optional<Path> saveInternal(Path directory) {
        Objects.requireNonNull(directory, "Directory cannot be null");
        ChartPlan plan = planForTerminal();
        return chartWorkflow.save(plan, directory);
    }

    private Optional<Path> saveInternal() {
        ChartPlan plan = planForTerminal();
        return chartWorkflow.save(plan);
    }

    private JFreeChart toChartInternal() {
        ChartPlan plan = planForTerminal();
        return chartWorkflow.render(plan);
    }

    /**
     * Stage representing the active chart or subplot. Intermediate operations can
     * add overlays or spawn child charts. Terminal operations consume the builder.
     */
    public interface ChartStage extends TerminalStage {

        /**
         * Adds an indicator overlay to the current chart.
         *
         * @param indicator the indicator to overlay on the chart
         * @return a styled overlay stage for configuring the overlay appearance
         */
        StyledOverlayStage withIndicatorOverlay(Indicator<Num> indicator);

        /**
         * Adds upper and lower channel boundaries as overlays on the current chart.
         *
         * @param upper the upper boundary indicator
         * @param lower the lower boundary indicator
         * @return a styled channel stage for configuring channel appearance
         */
        StyledChannelStage withChannelOverlay(Indicator<Num> upper, Indicator<Num> lower);

        /**
         * Adds upper, median, and lower channel boundaries as overlays on the current
         * chart.
         *
         * @param upper  the upper boundary indicator
         * @param median the median boundary indicator
         * @param lower  the lower boundary indicator
         * @return a styled channel stage for configuring channel appearance
         */
        StyledChannelStage withChannelOverlay(Indicator<Num> upper, Indicator<Num> median, Indicator<Num> lower);

        /**
         * Adds upper, median, and lower channel boundaries by wrapping a channel
         * indicator.
         *
         * @param channelIndicator the indicator providing channel boundary values
         * @return a styled channel stage for configuring channel appearance
         */
        StyledChannelStage withChannelOverlay(Indicator<? extends PriceChannel> channelIndicator);

        /**
         * Adds a trading record overlay to the current chart, displaying buy/sell
         * markers.
         *
         * @param tradingRecord the trading record containing buy and sell signals
         * @return this chart stage for further configuration
         */
        ChartStage withTradingRecordOverlay(TradingRecord tradingRecord);

        /**
         * Adds an analysis criterion overlay to the current chart, typically displayed
         * on a secondary axis.
         *
         * @param criterion     the analysis criterion to display
         * @param tradingRecord the trading record to evaluate the criterion against
         * @return a styled overlay stage for configuring the overlay appearance
         */
        StyledOverlayStage withAnalysisCriterionOverlay(AnalysisCriterion criterion, TradingRecord tradingRecord);

        /**
         * Creates a new sub-chart using an indicator as the base.
         *
         * @param indicator the indicator to use as the base for the sub-chart
         * @return a new chart stage for the sub-chart (the previous stage becomes
         *         inactive)
         */
        ChartStage withSubChart(Indicator<Num> indicator);

        /**
         * Creates a new sub-chart displaying a trading record.
         *
         * @param tradingRecord the trading record to display in the sub-chart
         * @return a new chart stage for the sub-chart (the previous stage becomes
         *         inactive)
         */
        ChartStage withSubChart(TradingRecord tradingRecord);

        /**
         * Creates a new sub-chart displaying an analysis criterion.
         *
         * @param criterion     the analysis criterion to display
         * @param tradingRecord the trading record to evaluate the criterion against
         * @return a new chart stage for the sub-chart (the previous stage becomes
         *         inactive)
         */
        ChartStage withSubChart(AnalysisCriterion criterion, TradingRecord tradingRecord);

        /**
         * Sets a custom title for the chart.
         *
         * @param title the desired chart title
         * @return this chart stage for further configuration
         */
        ChartStage withTitle(String title);

        /**
         * Sets the time axis mode for the chart domain axis.
         *
         * @param timeAxisMode the time axis mode to apply
         * @return this chart stage for further configuration
         * @since 0.23
         */
        ChartStage withTimeAxisMode(TimeAxisMode timeAxisMode);

        /**
         * Adds a horizontal marker (reference line) at the specified Y-axis value.
         * Useful for marking key levels on oscillators (e.g., RSI 50, 30, 70) or zero
         * lines (e.g., MACD 0).
         *
         * @param yValue the Y-axis value where the marker should be drawn
         * @return a styled marker stage for configuring the marker appearance
         */
        StyledMarkerStage withHorizontalMarker(double yValue);
    }

    /**
     * Stage returned immediately after adding an overlay that supports styling
     * tweaks such as color and line width.
     */
    public interface StyledOverlayStage extends ChartStage {

        /**
         * Sets the line color for the overlay.
         *
         * @param color the color to use for the overlay line
         * @return this styled overlay stage for method chaining
         */
        StyledOverlayStage withLineColor(Color color);

        /**
         * Sets the line width for the overlay.
         *
         * @param width the line width in pixels (must be greater than 0.05)
         * @return this styled overlay stage for method chaining
         * @throws IllegalArgumentException if width is less than or equal to 0.05
         */
        StyledOverlayStage withLineWidth(float width);

        /**
         * Controls whether the overlay should connect across NaN (missing) values.
         *
         * @param connectAcrossNaN if true, connects valid values across NaN gaps; if
         *                         false, splits into separate segments
         * @return this styled overlay stage for method chaining
         */
        StyledOverlayStage withConnectAcrossNaN(boolean connectAcrossNaN);

        /**
         * Sets a custom label for the overlay, used in the chart legend and axis
         * labels.
         *
         * @param label the custom label text
         * @return this styled overlay stage for method chaining
         */
        StyledOverlayStage withLabel(String label);

        /**
         * Sets the opacity for the overlay.
         *
         * @param opacity the opacity value between 0.0 (fully transparent) and 1.0
         *                (fully opaque)
         * @return this styled overlay stage for method chaining
         * @throws IllegalArgumentException if opacity is outside the range [0.0, 1.0]
         */
        StyledOverlayStage withOpacity(float opacity);
    }

    /**
     * Stage returned immediately after adding a horizontal marker that supports
     * styling tweaks such as color, line width, and opacity.
     */
    public interface StyledMarkerStage extends ChartStage {

        /**
         * Sets the line color for the marker.
         *
         * @param color the color to use for the marker line
         * @return this styled marker stage for method chaining
         */
        StyledMarkerStage withLineColor(Color color);

        /**
         * Sets the line width for the marker.
         *
         * @param width the line width in pixels (must be greater than 0.05)
         * @return this styled marker stage for method chaining
         * @throws IllegalArgumentException if width is less than or equal to 0.05
         */
        StyledMarkerStage withLineWidth(float width);

        /**
         * Sets the opacity for the marker.
         *
         * @param opacity the opacity value between 0.0 (fully transparent) and 1.0
         *                (fully opaque)
         * @return this styled marker stage for method chaining
         * @throws IllegalArgumentException if opacity is outside the range [0.0, 1.0]
         */
        StyledMarkerStage withOpacity(float opacity);
    }

    /**
     * Terminal operations consume the builder, similar to Java streams. Once a
     * terminal operation is invoked, the builder cannot be reused.
     */
    public interface TerminalStage {

        /**
         * Displays the chart in a new window using the default title.
         */
        void display();

        /**
         * Displays the chart in a new window with a custom title.
         *
         * @param windowTitle the title for the chart window
         */
        void display(String windowTitle);

        /**
         * Saves the chart to the default location and returns the path to the saved
         * file.
         *
         * @return an Optional containing the path to the saved chart file, or empty if
         *         saving failed
         */
        Optional<Path> save();

        /**
         * Saves the chart to the default directory with the specified filename.
         *
         * @param filename the name of the file to save
         * @return an Optional containing the path to the saved chart file, or empty if
         *         saving failed
         */
        Optional<Path> save(String filename);

        /**
         * Saves the chart to the specified directory with the specified filename.
         *
         * @param directory the directory where the chart should be saved
         * @param filename  the name of the file to save
         * @return an Optional containing the path to the saved chart file, or empty if
         *         saving failed
         */
        Optional<Path> save(Path directory, String filename);

        /**
         * Saves the chart to the specified directory with the specified filename.
         *
         * @param directory the directory path where the chart should be saved
         * @param filename  the name of the file to save
         * @return an Optional containing the path to the saved chart file, or empty if
         *         saving failed
         */
        Optional<Path> save(String directory, String filename);

        /**
         * Saves the chart to the specified directory with an auto-generated filename.
         *
         * @param directory the directory where the chart should be saved
         * @return an Optional containing the path to the saved chart file, or empty if
         *         saving failed
         */
        Optional<Path> save(Path directory);

        /**
         * Builds and returns the JFreeChart instance without displaying or saving it.
         *
         * @return the constructed JFreeChart
         */
        JFreeChart toChart();

        /**
         * Builds and returns the ChartPlan without rendering it. Useful for
         * programmatic inspection or custom rendering.
         *
         * @return the constructed ChartPlan
         */
        ChartPlan toPlan();
    }

    private class PlotStageImpl implements ChartStage {

        private final PlotContext context;
        private boolean acceptingCommands = true;

        private PlotStageImpl(PlotContext context) {
            this.context = context;
        }

        protected void ensureActive() {
            if (!acceptingCommands) {
                throw new IllegalStateException(
                        "This chart stage is no longer active. Continue from the stage returned by withSubChart().");
            }
        }

        @Override
        public StyledOverlayStage withIndicatorOverlay(Indicator<Num> indicator) {
            ensureActive();
            return addIndicatorOverlay(context, indicator, OverlayType.INDICATOR);
        }

        @Override
        public StyledChannelStage withChannelOverlay(Indicator<Num> upper, Indicator<Num> lower) {
            ensureActive();
            return addChannelOverlay(context, upper, null, lower);
        }

        @Override
        public StyledChannelStage withChannelOverlay(Indicator<Num> upper, Indicator<Num> median,
                Indicator<Num> lower) {
            ensureActive();
            return addChannelOverlay(context, upper, median, lower);
        }

        @Override
        public StyledChannelStage withChannelOverlay(Indicator<? extends PriceChannel> channelIndicator) {
            ensureActive();
            Objects.requireNonNull(channelIndicator, "Channel indicator cannot be null");
            ChannelBoundaryIndicator upper = new ChannelBoundaryIndicator(channelIndicator,
                    PriceChannel.Boundary.UPPER);
            ChannelBoundaryIndicator median = new ChannelBoundaryIndicator(channelIndicator,
                    PriceChannel.Boundary.MEDIAN);
            ChannelBoundaryIndicator lower = new ChannelBoundaryIndicator(channelIndicator,
                    PriceChannel.Boundary.LOWER);
            return addChannelOverlay(context, upper, median, lower);
        }

        @Override
        public ChartStage withTradingRecordOverlay(TradingRecord tradingRecord) {
            ensureActive();
            return addTradingRecordOverlay(context, tradingRecord);
        }

        @Override
        public StyledOverlayStage withAnalysisCriterionOverlay(AnalysisCriterion criterion,
                TradingRecord tradingRecord) {
            ensureActive();
            return addAnalysisOverlay(context, criterion, tradingRecord);
        }

        @Override
        public ChartStage withSubChart(Indicator<Num> indicator) {
            ensureActive();
            acceptingCommands = false;
            return addIndicatorSubChart(indicator);
        }

        @Override
        public ChartStage withSubChart(TradingRecord tradingRecord) {
            ensureActive();
            acceptingCommands = false;
            return addTradingRecordSubChart(tradingRecord);
        }

        @Override
        public ChartStage withSubChart(AnalysisCriterion criterion, TradingRecord tradingRecord) {
            ensureActive();
            acceptingCommands = false;
            return addAnalysisSubChart(criterion, tradingRecord);
        }

        @Override
        public ChartStage withTitle(String title) {
            ensureActive();
            customTitle = title;
            return this;
        }

        @Override
        public ChartStage withTimeAxisMode(TimeAxisMode timeAxisMode) {
            ensureActive();
            ChartBuilder.this.withTimeAxisMode(timeAxisMode);
            return this;
        }

        @Override
        public StyledMarkerStage withHorizontalMarker(double yValue) {
            ensureActive();
            return addHorizontalMarker(context, yValue);
        }

        @Override
        public void display() {
            ensureActive();
            acceptingCommands = false;
            displayInternal(null);
        }

        @Override
        public void display(String windowTitle) {
            ensureActive();
            acceptingCommands = false;
            displayInternal(windowTitle);
        }

        @Override
        public Optional<Path> save() {
            ensureActive();
            acceptingCommands = false;
            return saveInternal();
        }

        @Override
        public Optional<Path> save(String filename) {
            ensureActive();
            acceptingCommands = false;
            return saveInternal(filename);
        }

        @Override
        public Optional<Path> save(Path directory, String filename) {
            ensureActive();
            acceptingCommands = false;
            return saveInternal(directory, filename);
        }

        @Override
        public Optional<Path> save(String directory, String filename) {
            ensureActive();
            acceptingCommands = false;
            return saveInternal(directory, filename);
        }

        @Override
        public Optional<Path> save(Path directory) {
            ensureActive();
            acceptingCommands = false;
            return saveInternal(directory);
        }

        @Override
        public JFreeChart toChart() {
            ensureActive();
            acceptingCommands = false;
            return toChartInternal();
        }

        @Override
        public ChartPlan toPlan() {
            ensureActive();
            acceptingCommands = false;
            return planForTerminal();
        }
    }

    private final class StyledOverlayStageImpl extends PlotStageImpl implements StyledOverlayStage {

        private final OverlayContext overlay;

        private StyledOverlayStageImpl(PlotContext context, OverlayContext overlay) {
            super(context);
            this.overlay = overlay;
        }

        @Override
        public StyledOverlayStage withLineColor(Color color) {
            ensureOverlay();
            overlay.style.setColor(color);
            return this;
        }

        @Override
        public StyledOverlayStage withLineWidth(float width) {
            ensureOverlay();
            overlay.style.setLineWidth(width);
            return this;
        }

        @Override
        public StyledOverlayStage withConnectAcrossNaN(boolean connectAcrossNaN) {
            ensureOverlay();
            overlay.style.setConnectGaps(connectAcrossNaN);
            return this;
        }

        @Override
        public StyledOverlayStage withLabel(String label) {
            ensureOverlay();
            overlay.setLabel(label);
            return this;
        }

        @Override
        public StyledOverlayStage withOpacity(float opacity) {
            ensureOverlay();
            overlay.style.setOpacity(opacity);
            return this;
        }

        private void ensureOverlay() {
            if (overlay == null) {
                throw new IllegalStateException(
                        "Cannot style overlay because the previous overlay request was rejected due to axis incompatibility.");
            }
        }
    }

    private final class StyledChannelStageImpl extends PlotStageImpl implements StyledChannelStage {

        private final ChannelOverlayContext channelOverlay;

        private StyledChannelStageImpl(PlotContext context, ChannelOverlayContext channelOverlay) {
            super(context);
            this.channelOverlay = channelOverlay;
        }

        @Override
        public StyledChannelStage withLineColor(Color color) {
            ensureChannel();
            channelOverlay.setLineColor(color);
            return this;
        }

        @Override
        public StyledChannelStage withLineWidth(float width) {
            ensureChannel();
            channelOverlay.setLineWidth(width);
            return this;
        }

        @Override
        public StyledChannelStage withOpacity(float opacity) {
            ensureChannel();
            channelOverlay.setLineOpacity(opacity);
            return this;
        }

        @Override
        public StyledChannelStage withFillColor(Color color) {
            ensureChannel();
            channelOverlay.setFillColor(color);
            return this;
        }

        @Override
        public StyledChannelStage withFillOpacity(float opacity) {
            ensureChannel();
            channelOverlay.setFillOpacity(opacity);
            return this;
        }

        private void ensureChannel() {
            if (channelOverlay == null) {
                throw new IllegalStateException(
                        "Cannot style channel overlay because the previous channel request was rejected due to axis incompatibility.");
            }
        }
    }

    private final class StyledMarkerStageImpl extends PlotStageImpl implements StyledMarkerStage {

        private final HorizontalMarkerContext marker;

        private StyledMarkerStageImpl(PlotContext context, HorizontalMarkerContext marker) {
            super(context);
            this.marker = marker;
        }

        @Override
        public StyledMarkerStage withLineColor(Color color) {
            ensureActive();
            marker.style.setColor(color);
            return this;
        }

        @Override
        public StyledMarkerStage withLineWidth(float width) {
            ensureActive();
            marker.style.setLineWidth(width);
            return this;
        }

        @Override
        public StyledMarkerStage withOpacity(float opacity) {
            ensureActive();
            marker.style.setOpacity(opacity);
            return this;
        }
    }

    private static final class PlotContext {
        private final PlotType type;
        private final BarSeries series;
        private final Indicator<Num> baseIndicator;
        private final TradingRecord tradingRecord;
        private final AxisModel axisModel;
        private final List<OverlayContext> overlays = new ArrayList<>();
        private final List<ChannelOverlayContext> channelOverlays = new ArrayList<>();
        private final List<HorizontalMarkerContext> horizontalMarkers = new ArrayList<>();

        private PlotContext(PlotType type, BarSeries series, Indicator<Num> baseIndicator, TradingRecord tradingRecord,
                AxisModel axisModel) {
            this.type = type;
            this.series = series;
            this.baseIndicator = baseIndicator;
            this.tradingRecord = tradingRecord;
            this.axisModel = axisModel;
        }

        static PlotContext candlestick(BarSeries series) {
            AxisModel axisModel = new AxisModel(AxisRange.forSeries(series));
            return new PlotContext(PlotType.CANDLESTICK, series, null, null, axisModel);
        }

        static PlotContext indicator(BarSeries series, Indicator<Num> indicator) {
            AxisModel axisModel = new AxisModel(AxisRange.forIndicator(indicator));
            return new PlotContext(PlotType.INDICATOR, series, indicator, null, axisModel);
        }

        static PlotContext trading(BarSeries series, TradingRecord tradingRecord) {
            AxisRange range = AxisRange.forTradingRecord(tradingRecord);
            if (!range.isValid()) {
                range = AxisRange.forSeries(series);
            }
            AxisModel axisModel = new AxisModel(range);
            return new PlotContext(PlotType.TRADING_RECORD, series, new ClosePriceIndicator(series), tradingRecord,
                    axisModel);
        }
    }

    /**
     * Immutable definition of a complete chart, including the base plot, subplots,
     * and metadata. This class is used internally to represent the chart structure
     * before rendering.
     */
    public static final class ChartDefinition {
        private final PlotDefinition basePlot;
        private final List<PlotDefinition> subplots;
        private final ChartDefinitionMetadata metadata;

        ChartDefinition(PlotDefinition basePlot, List<PlotDefinition> subplots, ChartDefinitionMetadata metadata) {
            this.basePlot = basePlot;
            this.subplots = subplots;
            this.metadata = Objects.requireNonNull(metadata, "Chart metadata cannot be null");
        }

        /**
         * Returns the base plot definition.
         *
         * @return the base plot definition
         */
        public PlotDefinition basePlot() {
            return basePlot;
        }

        /**
         * Returns an immutable list of subplot definitions.
         *
         * @return the list of subplot definitions
         */
        public List<PlotDefinition> subplots() {
            return subplots;
        }

        /**
         * Returns the chart metadata (title, domain series, time axis mode).
         *
         * @return the chart metadata
         * @since 0.23
         */
        public ChartDefinitionMetadata metadata() {
            return metadata;
        }

        /**
         * Returns the chart title, or null if no custom title was set.
         *
         * @return the chart title, or null
         */
        public String title() {
            return metadata.title();
        }

        /**
         * Returns the shared domain series for the chart.
         *
         * @return the domain series
         * @since 0.23
         */
        public BarSeries domainSeries() {
            return metadata.domainSeries();
        }

        /**
         * Returns the time axis mode for the chart domain axis.
         *
         * @return the time axis mode
         * @since 0.23
         */
        public TimeAxisMode timeAxisMode() {
            return metadata.timeAxisMode();
        }
    }

    /**
     * Metadata about a chart definition, including the shared domain series and
     * time axis configuration.
     *
     * @since 0.23
     */
    public static final class ChartDefinitionMetadata {
        private final BarSeries domainSeries;
        private final String title;
        private final TimeAxisMode timeAxisMode;

        ChartDefinitionMetadata(BarSeries domainSeries, String title, TimeAxisMode timeAxisMode) {
            this.domainSeries = Objects.requireNonNull(domainSeries, "Domain series cannot be null");
            this.title = title;
            this.timeAxisMode = Objects.requireNonNull(timeAxisMode, "Time axis mode cannot be null");
        }

        public BarSeries domainSeries() {
            return domainSeries;
        }

        public String title() {
            return title;
        }

        public TimeAxisMode timeAxisMode() {
            return timeAxisMode;
        }
    }

    /**
     * Immutable definition of a single plot within a chart, including its type,
     * data series, base indicator (if applicable), trading record (if applicable),
     * and overlays.
     */
    public static final class PlotDefinition {
        private final PlotType type;
        private final BarSeries series;
        private final Indicator<Num> baseIndicator;
        private final TradingRecord tradingRecord;
        private final List<OverlayDefinition> overlays;
        private final List<ChannelOverlayDefinition> channelOverlays;
        private final List<HorizontalMarkerDefinition> horizontalMarkers;

        private PlotDefinition(PlotType type, BarSeries series, Indicator<Num> baseIndicator,
                TradingRecord tradingRecord, List<OverlayDefinition> overlays,
                List<ChannelOverlayDefinition> channelOverlays, List<HorizontalMarkerDefinition> horizontalMarkers) {
            this.type = type;
            this.series = series;
            this.baseIndicator = baseIndicator;
            this.tradingRecord = tradingRecord;
            this.overlays = overlays;
            this.channelOverlays = channelOverlays;
            this.horizontalMarkers = horizontalMarkers;
        }

        static PlotDefinition fromContext(PlotContext context) {
            List<OverlayDefinition> overlayDefinitions = new ArrayList<>();
            for (OverlayContext overlayContext : context.overlays) {
                overlayDefinitions.add(OverlayDefinition.fromContext(overlayContext));
            }
            List<ChannelOverlayDefinition> channelDefinitions = new ArrayList<>();
            for (ChannelOverlayContext channelContext : context.channelOverlays) {
                channelDefinitions.add(ChannelOverlayDefinition.fromContext(channelContext));
            }
            List<HorizontalMarkerDefinition> markerDefinitions = new ArrayList<>();
            for (HorizontalMarkerContext markerContext : context.horizontalMarkers) {
                markerDefinitions.add(HorizontalMarkerDefinition.fromContext(markerContext));
            }
            return new PlotDefinition(context.type, context.series, context.baseIndicator, context.tradingRecord,
                    Collections.unmodifiableList(overlayDefinitions), Collections.unmodifiableList(channelDefinitions),
                    Collections.unmodifiableList(markerDefinitions));
        }

        /**
         * Returns the type of this plot.
         *
         * @return the plot type
         */
        public PlotType type() {
            return type;
        }

        /**
         * Returns the bar series backing this plot.
         *
         * @return the bar series
         */
        public BarSeries series() {
            return series;
        }

        /**
         * Returns the base indicator for this plot, or null if this is a candlestick or
         * trading record plot.
         *
         * @return the base indicator, or null
         */
        public Indicator<Num> baseIndicator() {
            return baseIndicator;
        }

        /**
         * Returns the trading record for this plot, or null if this is not a trading
         * record plot.
         *
         * @return the trading record, or null
         */
        public TradingRecord tradingRecord() {
            return tradingRecord;
        }

        /**
         * Returns an immutable list of overlay definitions for this plot.
         *
         * @return the list of overlay definitions
         */
        public List<OverlayDefinition> overlays() {
            return overlays;
        }

        /**
         * Returns an immutable list of channel overlay definitions for this plot.
         *
         * @return the list of channel overlay definitions
         */
        public List<ChannelOverlayDefinition> channelOverlays() {
            return channelOverlays;
        }

        /**
         * Returns an immutable list of horizontal marker definitions for this plot.
         *
         * @return the list of horizontal marker definitions
         */
        public List<HorizontalMarkerDefinition> horizontalMarkers() {
            return horizontalMarkers;
        }
    }

    /**
     * Stage returned after adding a channel overlay that supports styling the
     * channel lines and fill.
     */
    public interface StyledChannelStage extends ChartStage {

        /**
         * Sets the line color for the channel boundaries.
         *
         * @param color the color to use for channel lines
         * @return this styled channel stage for method chaining
         */
        StyledChannelStage withLineColor(Color color);

        /**
         * Sets the line width for the channel boundaries.
         *
         * @param width the line width in pixels (must be greater than 0.05)
         * @return this styled channel stage for method chaining
         * @throws IllegalArgumentException if width is less than or equal to 0.05
         */
        StyledChannelStage withLineWidth(float width);

        /**
         * Sets the opacity for the channel boundary lines.
         *
         * @param opacity the opacity value between 0.0 (fully transparent) and 1.0
         *                (fully opaque)
         * @return this styled channel stage for method chaining
         * @throws IllegalArgumentException if opacity is outside the range [0.0, 1.0]
         */
        StyledChannelStage withOpacity(float opacity);

        /**
         * Sets the fill color for the channel interior.
         *
         * @param color the color to use for the channel fill
         * @return this styled channel stage for method chaining
         */
        StyledChannelStage withFillColor(Color color);

        /**
         * Sets the opacity for the channel fill.
         *
         * @param opacity the opacity value between 0.0 (fully transparent) and 1.0
         *                (fully opaque)
         * @return this styled channel stage for method chaining
         * @throws IllegalArgumentException if opacity is outside the range [0.0, 1.0]
         */
        StyledChannelStage withFillOpacity(float opacity);
    }

    /**
     * Immutable definition of a channel overlay fill between upper and lower
     * boundaries.
     */
    public static final class ChannelOverlayDefinition {
        private final Indicator<Num> upper;
        private final Indicator<Num> lower;
        private final AxisSlot axisSlot;
        private final Color fillColor;
        private final float fillOpacity;

        private ChannelOverlayDefinition(Indicator<Num> upper, Indicator<Num> lower, AxisSlot axisSlot, Color fillColor,
                float fillOpacity) {
            this.upper = upper;
            this.lower = lower;
            this.axisSlot = axisSlot;
            this.fillColor = fillColor;
            this.fillOpacity = fillOpacity;
        }

        static ChannelOverlayDefinition fromContext(ChannelOverlayContext context) {
            return new ChannelOverlayDefinition(context.upperIndicator, context.lowerIndicator, context.axisSlot,
                    context.fillStyle.color(), context.fillStyle.opacity());
        }

        /**
         * Returns the upper boundary indicator.
         *
         * @return the upper boundary indicator
         */
        public Indicator<Num> upper() {
            return upper;
        }

        /**
         * Returns the lower boundary indicator.
         *
         * @return the lower boundary indicator
         */
        public Indicator<Num> lower() {
            return lower;
        }

        /**
         * Returns the axis slot where the channel fill is rendered.
         *
         * @return the axis slot
         */
        public AxisSlot axisSlot() {
            return axisSlot;
        }

        /**
         * Returns the base fill color for the channel interior.
         *
         * @return the fill color
         */
        public Color fillColor() {
            return fillColor;
        }

        /**
         * Returns the opacity for the channel fill.
         *
         * @return the fill opacity
         */
        public float fillOpacity() {
            return fillOpacity;
        }
    }

    /**
     * Immutable definition of an overlay on a plot, including its type, data
     * source, axis assignment, styling, and label.
     */
    public static final class OverlayDefinition {
        private final OverlayType type;
        private final Indicator<Num> indicator;
        private final TradingRecord tradingRecord;
        private final AxisSlot axisSlot;
        private final OverlayStyle style;
        private final String label;

        private OverlayDefinition(OverlayType type, Indicator<Num> indicator, TradingRecord tradingRecord,
                AxisSlot axisSlot, OverlayStyle style, String label) {
            this.type = type;
            this.indicator = indicator;
            this.tradingRecord = tradingRecord;
            this.axisSlot = axisSlot;
            this.style = style;
            this.label = label;
        }

        static OverlayDefinition fromContext(OverlayContext context) {
            return new OverlayDefinition(context.type, context.indicator, context.tradingRecord, context.axisSlot,
                    context.style, context.label);
        }

        /**
         * Returns the type of this overlay.
         *
         * @return the overlay type
         */
        public OverlayType type() {
            return type;
        }

        /**
         * Returns the indicator for this overlay, or null if this is a trading record
         * overlay.
         *
         * @return the indicator, or null
         */
        public Indicator<Num> indicator() {
            return indicator;
        }

        /**
         * Returns the trading record for this overlay, or null if this is not a trading
         * record overlay.
         *
         * @return the trading record, or null
         */
        public TradingRecord tradingRecord() {
            return tradingRecord;
        }

        /**
         * Returns the axis slot (primary or secondary) where this overlay is displayed.
         *
         * @return the axis slot
         */
        public AxisSlot axisSlot() {
            return axisSlot;
        }

        /**
         * Returns the styling information for this overlay.
         *
         * @return the overlay style
         */
        public OverlayStyle style() {
            return style;
        }

        /**
         * Returns the label for this overlay, or null if no custom label was set.
         *
         * @return the overlay label, or null
         */
        public String label() {
            return label;
        }
    }

    private static final class ChannelOverlayContext {
        private final Indicator<Num> upperIndicator;
        private final Indicator<Num> lowerIndicator;
        private final Indicator<Num> medianIndicator;
        private final AxisSlot axisSlot;
        private final OverlayStyle lineStyle;
        private final OverlayStyle medianStyle;
        private final ChannelFillStyle fillStyle;
        private boolean fillColorCustom;

        private ChannelOverlayContext(Indicator<Num> upperIndicator, Indicator<Num> lowerIndicator,
                Indicator<Num> medianIndicator, AxisSlot axisSlot, OverlayStyle lineStyle, OverlayStyle medianStyle,
                ChannelFillStyle fillStyle) {
            this.upperIndicator = upperIndicator;
            this.lowerIndicator = lowerIndicator;
            this.medianIndicator = medianIndicator;
            this.axisSlot = axisSlot;
            this.lineStyle = lineStyle;
            this.medianStyle = medianStyle;
            this.fillStyle = fillStyle;
        }

        private void setLineColor(Color color) {
            lineStyle.setColor(color);
            medianStyle.setColor(color);
            if (!fillColorCustom) {
                fillStyle.setColor(color);
            }
        }

        private void setLineWidth(float width) {
            lineStyle.setLineWidth(width);
            medianStyle.setLineWidth(width);
        }

        private void setLineOpacity(float opacity) {
            lineStyle.setOpacity(opacity);
            medianStyle.setOpacity(opacity);
        }

        private void setFillColor(Color color) {
            fillStyle.setColor(color);
            fillColorCustom = true;
        }

        private void setFillOpacity(float opacity) {
            fillStyle.setOpacity(opacity);
        }
    }

    private static final class OverlayContext {
        private final OverlayType type;
        private final Indicator<Num> indicator;
        private final TradingRecord tradingRecord;
        private final AxisSlot axisSlot;
        private final OverlayStyle style;
        private String label;

        private OverlayContext(OverlayType type, Indicator<Num> indicator, TradingRecord tradingRecord,
                AxisSlot axisSlot, OverlayStyle style, String label) {
            this.type = type;
            this.indicator = indicator;
            this.tradingRecord = tradingRecord;
            this.axisSlot = axisSlot;
            this.style = style;
            this.label = label;
        }

        static OverlayContext indicator(OverlayType type, Indicator<Num> indicator, AxisSlot axis, Color defaultColor,
                String label) {
            return new OverlayContext(type, indicator, null, axis, OverlayStyle.defaultStyle(defaultColor), label);
        }

        static OverlayContext indicator(OverlayType type, Indicator<Num> indicator, AxisSlot axis, OverlayStyle style,
                String label) {
            return new OverlayContext(type, indicator, null, axis, style, label);
        }

        static OverlayContext tradingRecord(TradingRecord tradingRecord) {
            return new OverlayContext(OverlayType.TRADING_RECORD, null, tradingRecord, AxisSlot.PRIMARY,
                    OverlayStyle.defaultStyle(Color.LIGHT_GRAY), null);
        }

        void setLabel(String label) {
            this.label = label;
        }
    }

    /**
     * Immutable definition of a horizontal marker (reference line) on a plot,
     * including its Y-axis value and styling.
     */
    public static final class HorizontalMarkerDefinition {
        private final double yValue;
        private final OverlayStyle style;

        private HorizontalMarkerDefinition(double yValue, OverlayStyle style) {
            this.yValue = yValue;
            this.style = style;
        }

        static HorizontalMarkerDefinition fromContext(HorizontalMarkerContext context) {
            return new HorizontalMarkerDefinition(context.yValue, context.style);
        }

        /**
         * Returns the Y-axis value where the marker should be drawn.
         *
         * @return the Y-axis value
         */
        public double yValue() {
            return yValue;
        }

        /**
         * Returns the style configuration for this marker.
         *
         * @return the marker style
         */
        public OverlayStyle style() {
            return style;
        }
    }

    private static final class HorizontalMarkerContext {
        private final double yValue;
        private final OverlayStyle style;

        private HorizontalMarkerContext(double yValue, OverlayStyle style) {
            this.yValue = yValue;
            this.style = style;
        }

        static HorizontalMarkerContext create(double yValue, Color defaultColor) {
            return new HorizontalMarkerContext(yValue, OverlayStyle.defaultStyle(defaultColor));
        }
    }

    /**
     * Type of plot that can be displayed in a chart.
     */
    public enum PlotType {
        /** A candlestick (OHLCV) chart displaying price bars. */
        CANDLESTICK,
        /** A line chart displaying an indicator. */
        INDICATOR,
        /** A chart displaying a trading record with buy/sell markers. */
        TRADING_RECORD
    }

    /**
     * Type of overlay that can be added to a plot.
     */
    public enum OverlayType {
        /** An indicator overlay displayed as a line. */
        INDICATOR,
        /** A trading record overlay displaying buy/sell markers. */
        TRADING_RECORD,
        /** An analysis criterion overlay, typically displayed on a secondary axis. */
        ANALYSIS_CRITERION
    }

    /**
     * Axis slot where an overlay can be displayed.
     */
    public enum AxisSlot {
        /** The primary (left) Y-axis. */
        PRIMARY,
        /** The secondary (right) Y-axis. */
        SECONDARY
    }

    private enum AxisAssignment {
        PRIMARY, SECONDARY, REJECTED
    }

    private static final class AxisModel {
        private AxisRange primary;
        private AxisRange secondary;

        private AxisModel(AxisRange primary) {
            this.primary = primary;
        }

        AxisAssignment assign(AxisRange candidate, boolean preferSecondary) {
            AxisRange normalizedCandidate = candidate != null ? candidate : new AxisRange(Double.NaN, Double.NaN);
            if (preferSecondary) {
                AxisAssignment secondaryAssignment = assignToSecondary(normalizedCandidate);
                if (secondaryAssignment == AxisAssignment.SECONDARY) {
                    return AxisAssignment.SECONDARY;
                }
            }
            if (primary == null) {
                primary = normalizedCandidate;
                return AxisAssignment.PRIMARY;
            }
            if (!normalizedCandidate.isValid() || !primary.isValid() || primary.overlaps(normalizedCandidate)) {
                primary = primary.merge(normalizedCandidate);
                return AxisAssignment.PRIMARY;
            }
            AxisAssignment secondaryAssignment = assignToSecondary(normalizedCandidate);
            if (secondaryAssignment == AxisAssignment.SECONDARY) {
                return AxisAssignment.SECONDARY;
            }
            return AxisAssignment.REJECTED;
        }

        private AxisAssignment assignToSecondary(AxisRange candidate) {
            if (secondary == null || !secondary.isValid()) {
                secondary = candidate;
                return AxisAssignment.SECONDARY;
            }
            if (candidate == null || !candidate.isValid()) {
                return AxisAssignment.SECONDARY;
            }
            if (secondary.overlaps(candidate)) {
                secondary = secondary.merge(candidate);
                return AxisAssignment.SECONDARY;
            }
            return AxisAssignment.REJECTED;
        }
    }

    private static final class AxisRange {
        private final double min;
        private final double max;

        private AxisRange(double min, double max) {
            this.min = min;
            this.max = max;
        }

        static AxisRange forSeries(BarSeries series) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Bar bar = series.getBar(i);
                min = Math.min(min, bar.getLowPrice().doubleValue());
                max = Math.max(max, bar.getHighPrice().doubleValue());
            }
            return new AxisRange(min, max);
        }

        static AxisRange forIndicator(Indicator<Num> indicator) {
            BarSeries series = indicator.getBarSeries();
            if (series == null || series.getBarCount() == 0) {
                return new AxisRange(Double.NaN, Double.NaN);
            }
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
                Num value = indicator.getValue(i);
                if (Num.isNaNOrNull(value)) {
                    continue;
                }
                double v = value.doubleValue();
                min = Math.min(min, v);
                max = Math.max(max, v);
            }
            return new AxisRange(min, max);
        }

        static AxisRange forTradingRecord(TradingRecord tradingRecord) {
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (Trade trade : tradingRecord.getTrades()) {
                if (Num.isNaNOrNull(trade.getPricePerAsset())) {
                    continue;
                }
                double price = trade.getPricePerAsset().doubleValue();
                min = Math.min(min, price);
                max = Math.max(max, price);
            }
            if (min == Double.POSITIVE_INFINITY || max == Double.NEGATIVE_INFINITY) {
                return new AxisRange(Double.NaN, Double.NaN);
            }
            return new AxisRange(min, max);
        }

        boolean isValid() {
            return !Double.isNaN(min) && !Double.isNaN(max) && min != Double.POSITIVE_INFINITY
                    && max != Double.NEGATIVE_INFINITY;
        }

        boolean overlaps(AxisRange other) {
            if (other == null || !other.isValid() || !isValid()) {
                return true;
            }
            return this.max >= other.min && other.max >= this.min;
        }

        AxisRange merge(AxisRange other) {
            if (other == null || !other.isValid()) {
                return this;
            }
            if (!isValid()) {
                return other;
            }
            return new AxisRange(Math.min(this.min, other.min), Math.max(this.max, other.max));
        }
    }

    /**
     * Mutable style configuration for chart overlays, including color, line width,
     * gap connection behavior, and opacity.
     */
    public static final class OverlayStyle {
        private Color color;
        private float lineWidth;
        private boolean connectGaps;
        private float opacity;
        private float[] dashPattern;

        private OverlayStyle(Color color, float lineWidth, boolean connectGaps, float opacity, float[] dashPattern) {
            this.color = color;
            this.lineWidth = lineWidth;
            this.connectGaps = connectGaps;
            this.opacity = opacity;
            this.dashPattern = dashPattern == null ? null : dashPattern.clone();
        }

        static OverlayStyle defaultStyle(Color color) {
            return new OverlayStyle(color, 1.6f, false, 1.0f, null);
        }

        /**
         * Returns the color for the overlay line.
         *
         * @return the line color
         */
        public Color color() {
            return color;
        }

        /**
         * Returns the line width in pixels.
         *
         * @return the line width
         */
        public float lineWidth() {
            return lineWidth;
        }

        /**
         * Returns whether the overlay connects across NaN (missing) values.
         *
         * @return true if gaps are connected, false if they create separate segments
         */
        public boolean connectGaps() {
            return connectGaps;
        }

        /**
         * Returns the opacity value for the overlay.
         *
         * @return the opacity value between 0.0 (fully transparent) and 1.0 (fully
         *         opaque)
         */
        public float opacity() {
            return opacity;
        }

        /**
         * Returns the dash pattern for the overlay line, or null for solid lines.
         *
         * @return the dash pattern, or null
         */
        public float[] dashPattern() {
            return dashPattern == null ? null : dashPattern.clone();
        }

        /**
         * Sets the color for the overlay line.
         *
         * @param color the line color (must not be null)
         * @throws NullPointerException if color is null
         */
        public void setColor(Color color) {
            this.color = Objects.requireNonNull(color, "Color cannot be null");
        }

        /**
         * Sets the line width in pixels.
         *
         * @param width the line width (must be greater than 0.05)
         * @throws IllegalArgumentException if width is less than or equal to 0.05
         */
        public void setLineWidth(float width) {
            if (width <= 0.05f) {
                throw new IllegalArgumentException("Line width must be positive");
            }
            this.lineWidth = width;
        }

        /**
         * Sets whether the overlay should connect across NaN (missing) values.
         *
         * @param connectGaps if true, connects valid values across NaN gaps; if false,
         *                    splits into separate segments
         */
        public void setConnectGaps(boolean connectGaps) {
            this.connectGaps = connectGaps;
        }

        /**
         * Sets the opacity for the overlay.
         *
         * @param opacity the opacity value between 0.0 (fully transparent) and 1.0
         *                (fully opaque)
         * @throws IllegalArgumentException if opacity is outside the range [0.0, 1.0]
         */
        public void setOpacity(float opacity) {
            if (opacity < 0.0f || opacity > 1.0f) {
                throw new IllegalArgumentException("Opacity must be between 0.0 and 1.0");
            }
            this.opacity = opacity;
        }

        /**
         * Sets a dash pattern for the overlay line.
         *
         * @param dashPattern the dash pattern (null for solid lines)
         */
        public void setDashPattern(float[] dashPattern) {
            this.dashPattern = dashPattern == null ? null : dashPattern.clone();
        }
    }

    private static final class ChannelFillStyle {
        private Color color;
        private float opacity;

        private ChannelFillStyle(Color color, float opacity) {
            this.color = Objects.requireNonNull(color, "Color cannot be null");
            setOpacity(opacity);
        }

        static ChannelFillStyle defaultStyle(Color color, float opacity) {
            return new ChannelFillStyle(color, opacity);
        }

        public Color color() {
            return color;
        }

        public float opacity() {
            return opacity;
        }

        public void setColor(Color color) {
            this.color = Objects.requireNonNull(color, "Color cannot be null");
        }

        public void setOpacity(float opacity) {
            if (opacity < 0.0f || opacity > 1.0f) {
                throw new IllegalArgumentException("Opacity must be between 0.0 and 1.0");
            }
            this.opacity = opacity;
        }
    }

    private static final class OverlayColorPalette {
        private static final Color[] DEFAULT_COLORS = { new Color(0x03DAC6), new Color(0xF05454), new Color(0xF6C90E),
                new Color(0x90CAF9), new Color(0xCE93D8), new Color(0x80CBC4) };

        private int index;

        Color nextColor() {
            Color color = DEFAULT_COLORS[index % DEFAULT_COLORS.length];
            index++;
            return color;
        }
    }
}
