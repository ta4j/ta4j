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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.chart.JFreeChart;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.Num;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import ta4jexamples.charting.AnalysisCriterionIndicator;
import ta4jexamples.charting.compose.TradingChartFactory;
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

    private final ChartWorkflow chartWorkflow;
    private final TradingChartFactory chartFactory;
    private final OverlayColorPalette colorPalette = new OverlayColorPalette();
    private final List<PlotContext> plots = new ArrayList<>();

    private boolean consumed;
    private String customTitle;
    private BarSeries domainSeries;

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
    }

    private void markConsumed() {
        this.consumed = true;
    }

    private void validateSeriesHasBars(BarSeries series) {
        if (series.getBarCount() == 0) {
            throw new IllegalArgumentException("Series must contain at least one bar");
        }
    }

    private BarSeries requireIndicatorSeries(Indicator<Num> indicator) {
        BarSeries series = indicator.getBarSeries();
        if (series == null) {
            throw new IllegalArgumentException("Indicator " + indicator + " is not attached to a BarSeries");
        }
        validateSeriesHasBars(series);
        return series;
    }

    private StyledOverlayStage addIndicatorOverlay(PlotContext context, Indicator<Num> indicator, OverlayType type) {
        return addIndicatorOverlay(context, indicator, type, false);
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
        OverlayContext overlay = OverlayContext.indicator(type, indicator, slot, colorPalette.nextColor());
        context.overlays.add(overlay);
        return new StyledOverlayStageImpl(context, overlay);
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
        return new ChartDefinition(base, subplots, customTitle);
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

        StyledOverlayStage withIndicatorOverlay(Indicator<Num> indicator);

        ChartStage withTradingRecordOverlay(TradingRecord tradingRecord);

        StyledOverlayStage withAnalysisCriterionOverlay(AnalysisCriterion criterion, TradingRecord tradingRecord);

        ChartStage withSubChart(Indicator<Num> indicator);

        ChartStage withSubChart(TradingRecord tradingRecord);

        ChartStage withSubChart(AnalysisCriterion criterion, TradingRecord tradingRecord);

        ChartStage withTitle(String title);
    }

    /**
     * Stage returned immediately after adding an overlay that supports styling
     * tweaks such as color and line width.
     */
    public interface StyledOverlayStage extends ChartStage {

        StyledOverlayStage withLineColor(Color color);

        StyledOverlayStage withLineWidth(float width);
    }

    /**
     * Terminal operations consume the builder, similar to Java streams.
     */
    public interface TerminalStage {

        void display();

        void display(String windowTitle);

        Optional<Path> save();

        Optional<Path> save(String filename);

        Optional<Path> save(Path directory, String filename);

        Optional<Path> save(String directory, String filename);

        Optional<Path> save(Path directory);

        JFreeChart toChart();

        ChartPlan toPlan();
    }

    private class PlotStageImpl implements ChartStage {

        private final PlotContext context;
        private boolean acceptingCommands = true;

        private PlotStageImpl(PlotContext context) {
            this.context = context;
        }

        private void ensureActive() {
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

        private void ensureOverlay() {
            if (overlay == null) {
                throw new IllegalStateException(
                        "Cannot style overlay because the previous overlay request was rejected due to axis incompatibility.");
            }
        }
    }

    private static final class PlotContext {
        private final PlotType type;
        private final BarSeries series;
        private final Indicator<Num> baseIndicator;
        private final TradingRecord tradingRecord;
        private final AxisModel axisModel;
        private final List<OverlayContext> overlays = new ArrayList<>();

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

    public static final class ChartDefinition {
        private final PlotDefinition basePlot;
        private final List<PlotDefinition> subplots;
        private final String title;

        ChartDefinition(PlotDefinition basePlot, List<PlotDefinition> subplots, String title) {
            this.basePlot = basePlot;
            this.subplots = subplots;
            this.title = title;
        }

        public PlotDefinition basePlot() {
            return basePlot;
        }

        public List<PlotDefinition> subplots() {
            return subplots;
        }

        public String title() {
            return title;
        }
    }

    public static final class PlotDefinition {
        private final PlotType type;
        private final BarSeries series;
        private final Indicator<Num> baseIndicator;
        private final TradingRecord tradingRecord;
        private final List<OverlayDefinition> overlays;

        private PlotDefinition(PlotType type, BarSeries series, Indicator<Num> baseIndicator,
                TradingRecord tradingRecord, List<OverlayDefinition> overlays) {
            this.type = type;
            this.series = series;
            this.baseIndicator = baseIndicator;
            this.tradingRecord = tradingRecord;
            this.overlays = overlays;
        }

        static PlotDefinition fromContext(PlotContext context) {
            List<OverlayDefinition> overlayDefinitions = new ArrayList<>();
            for (OverlayContext overlayContext : context.overlays) {
                overlayDefinitions.add(OverlayDefinition.fromContext(overlayContext));
            }
            return new PlotDefinition(context.type, context.series, context.baseIndicator, context.tradingRecord,
                    Collections.unmodifiableList(overlayDefinitions));
        }

        public PlotType type() {
            return type;
        }

        public BarSeries series() {
            return series;
        }

        public Indicator<Num> baseIndicator() {
            return baseIndicator;
        }

        public TradingRecord tradingRecord() {
            return tradingRecord;
        }

        public List<OverlayDefinition> overlays() {
            return overlays;
        }
    }

    public static final class OverlayDefinition {
        private final OverlayType type;
        private final Indicator<Num> indicator;
        private final TradingRecord tradingRecord;
        private final AxisSlot axisSlot;
        private final OverlayStyle style;

        private OverlayDefinition(OverlayType type, Indicator<Num> indicator, TradingRecord tradingRecord,
                AxisSlot axisSlot, OverlayStyle style) {
            this.type = type;
            this.indicator = indicator;
            this.tradingRecord = tradingRecord;
            this.axisSlot = axisSlot;
            this.style = style;
        }

        static OverlayDefinition fromContext(OverlayContext context) {
            return new OverlayDefinition(context.type, context.indicator, context.tradingRecord, context.axisSlot,
                    context.style);
        }

        public OverlayType type() {
            return type;
        }

        public Indicator<Num> indicator() {
            return indicator;
        }

        public TradingRecord tradingRecord() {
            return tradingRecord;
        }

        public AxisSlot axisSlot() {
            return axisSlot;
        }

        public OverlayStyle style() {
            return style;
        }
    }

    private static final class OverlayContext {
        private final OverlayType type;
        private final Indicator<Num> indicator;
        private final TradingRecord tradingRecord;
        private final AxisSlot axisSlot;
        private final OverlayStyle style;

        private OverlayContext(OverlayType type, Indicator<Num> indicator, TradingRecord tradingRecord,
                AxisSlot axisSlot, OverlayStyle style) {
            this.type = type;
            this.indicator = indicator;
            this.tradingRecord = tradingRecord;
            this.axisSlot = axisSlot;
            this.style = style;
        }

        static OverlayContext indicator(OverlayType type, Indicator<Num> indicator, AxisSlot axis, Color defaultColor) {
            return new OverlayContext(type, indicator, null, axis, OverlayStyle.defaultStyle(defaultColor));
        }

        static OverlayContext tradingRecord(TradingRecord tradingRecord) {
            return new OverlayContext(OverlayType.TRADING_RECORD, null, tradingRecord, AxisSlot.PRIMARY,
                    OverlayStyle.defaultStyle(Color.LIGHT_GRAY));
        }
    }

    public enum PlotType {
        CANDLESTICK, INDICATOR, TRADING_RECORD
    }

    public enum OverlayType {
        INDICATOR, TRADING_RECORD, ANALYSIS_CRITERION
    }

    public enum AxisSlot {
        PRIMARY, SECONDARY
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
                if (value == null || value.isNaN()) {
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
                if (trade.getPricePerAsset() == null || trade.getPricePerAsset().isNaN()) {
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

    public static final class OverlayStyle {
        private Color color;
        private float lineWidth;

        private OverlayStyle(Color color, float lineWidth) {
            this.color = color;
            this.lineWidth = lineWidth;
        }

        static OverlayStyle defaultStyle(Color color) {
            return new OverlayStyle(color, 1.6f);
        }

        public Color color() {
            return color;
        }

        public float lineWidth() {
            return lineWidth;
        }

        public void setColor(Color color) {
            this.color = Objects.requireNonNull(color, "Color cannot be null");
        }

        public void setLineWidth(float width) {
            if (width <= 0.05f) {
                throw new IllegalArgumentException("Line width must be positive");
            }
            this.lineWidth = width;
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
