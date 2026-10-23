/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.compose;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.text.NumberFormat;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.labels.XYToolTipGenerator;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.AbstractIndicator;
import org.ta4j.core.num.Num;

import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.builder.TimeAxisMode;
import ta4jexamples.charting.workflow.ChartWorkflow;

class TradingChartFactoryConcurrentCaptureTest {

    private static final Instant ORIGIN = Instant.parse("2024-01-01T12:00:00Z");

    enum Mutation {
        APPEND_EVICT, CLEAR
    }

    enum Entry {
        INDICATOR, TRADING_RECORD_INDICATORS, DUAL
    }

    @ParameterizedTest
    @EnumSource(Mutation.class)
    void candlePricesAndDatesSurviveMutationDuringConstruction(Mutation mutation) {
        TradingChartFactory factory = new TradingChartFactory();
        JFreeChart expected = factory.createTradingRecordChart(series(), "Capture", new BaseTradingRecord(),
                TimeAxisMode.BAR_INDEX);
        Interleaving source = new Interleaving(mutation);
        source.armed = true;

        JFreeChart actual = factory.createTradingRecordChart(source.view, "Capture", new BaseTradingRecord(),
                TimeAxisMode.BAR_INDEX);

        assertEquals(signature(expected), signature(actual));
        assertEquals(mutation == Mutation.CLEAR ? 0 : 4, source.source.getBarCount());
    }

    @ParameterizedTest
    @EnumSource(Entry.class)
    void indicatorCoordinatesShareTheCapturedDatesWithoutHoldingTheReadLease(Entry entry) {
        Interleaving reference = new Interleaving(Mutation.CLEAR);
        Interleaving source = new Interleaving(Mutation.CLEAR);
        JFreeChart expected = indicatorChart(entry, reference);
        source.armed = true;

        JFreeChart actual = indicatorChart(entry, source);

        assertEquals(signature(expected), signature(actual));
        assertEquals(0, source.source.getBarCount());
    }

    @Test
    void composedOverlaysKeepThePlanWindowWhenTheirLiveSourceIsCleared() {
        Interleaving source = new Interleaving(Mutation.CLEAR);
        Indicator<Num> indicator = indexIndicator(source.view, source);
        ChartPlan plan = new ChartWorkflow().builder()
                .withTimeAxisMode(TimeAxisMode.BAR_INDEX)
                .withSeries(source.view)
                .withIndicatorOverlay(indicator)
                .withSubChart(indicator)
                .toPlan();
        TradingChartFactory factory = new TradingChartFactory();
        List<String> expected = signature(factory.compose(plan.context()));
        source.armed = true;

        JFreeChart actual = factory.compose(plan.context());

        assertEquals(expected, signature(actual));
        assertEquals(0, source.source.getBarCount());
    }

    @ParameterizedTest
    @EnumSource(TimeAxisMode.class)
    void composedWindowKeepsAbsoluteIndicesForCandlesOverlaysAndTrades(TimeAxisMode mode) {
        ConcurrentBarSeries source = series();
        BaseTradingRecord trades = new BaseTradingRecord();
        trades.enter(3, source.numFactory().numOf(103), source.numFactory().one());
        trades.exit(5, source.numFactory().numOf(105), source.numFactory().one());
        TradingChartFactory factory = new TradingChartFactory();
        JFreeChart reference = factory.createTradingRecordChart(source, "Capture", trades, mode);
        Indicator<Num> indicator = indexIndicator(source, null);
        JFreeChart composed = factory.compose(new ChartWorkflow().builder()
                .withTimeAxisMode(mode)
                .withSeries(source)
                .withTradingRecordOverlay(trades)
                .withIndicatorOverlay(indicator)
                .withSubChart(indicator)
                .toPlan()
                .context());

        XYPlot expected = plots(reference).getFirst();
        XYPlot actual = plots(composed).getFirst();
        assertEquals(datasetSignature(expected, 0), datasetSignature(actual, 0));
        assertEquals(
                expected.getAnnotations()
                        .stream()
                        .map(annotation -> (XYTextAnnotation) annotation)
                        .map(annotation -> List.of(annotation.getX(), annotation.getY()))
                        .toList(),
                actual.getAnnotations()
                        .stream()
                        .map(annotation -> (XYTextAnnotation) annotation)
                        .map(annotation -> List.of(annotation.getX(), annotation.getY()))
                        .toList());
        XYDataset overlay = actual.getDataset(actual.getDatasetCount() - 1);
        XYDataset subplot = plots(composed).get(1).getDataset();
        assertEquals(2.0, mode == TimeAxisMode.BAR_INDEX ? overlay.getXValue(0, 0) : subplot.getYValue(0, 0));
        if (mode == TimeAxisMode.BAR_INDEX) {
            NumberFormat axis = ((NumberAxis) actual.getDomainAxis()).getNumberFormatOverride();
            assertEquals(((NumberAxis) expected.getDomainAxis()).getNumberFormatOverride().format(2), axis.format(2));
            assertEquals(axis.format(2),
                    ((NumberAxis) plots(composed).get(1).getDomainAxis()).getNumberFormatOverride().format(2));
        }
    }

    @ParameterizedTest
    @CsvSource({ "REAL_TIME,false", "REAL_TIME,true", "BAR_INDEX,false", "BAR_INDEX,true" })
    void seriesLessSubplotsKeepThePlotWindowWhenEvaluationClearsItsSource(TimeAxisMode mode, boolean tradingRecord) {
        ConcurrentBarSeries source = series();
        List<Long> timestamps = source.getBarData().stream().map(bar -> bar.getEndTime().toEpochMilli()).toList();
        Num constant = source.numFactory().numOf(7);
        Indicator<Num> indicator = new AbstractIndicator<Num>(source) {
            @Override
            public BarSeries getBarSeries() {
                return null;
            }

            @Override
            public Num getValue(int index) {
                source.clear();
                return constant;
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }
        };
        TradingChartFactory factory = new TradingChartFactory();

        JFreeChart chart = tradingRecord
                ? factory.createTradingRecordChart(source, "Capture", new BaseTradingRecord(), mode, indicator)
                : factory.createIndicatorChart(source, mode, indicator);

        XYDataset subplot = plots(chart).get(1).getDataset();
        assertEquals(4, subplot.getItemCount(0));
        for (int i = 0; i < 4; i++) {
            assertEquals(mode == TimeAxisMode.BAR_INDEX ? 2 + i : timestamps.get(i).doubleValue(),
                    subplot.getXValue(0, i));
            assertEquals(7.0, subplot.getYValue(0, i));
        }
        assertEquals(0, source.getBarCount());
    }

    private static JFreeChart indicatorChart(Entry entry, Interleaving source) {
        TradingChartFactory factory = new TradingChartFactory();
        Indicator<Num> indicator = indexIndicator(source.view, source);
        return switch (entry) {
        case INDICATOR -> factory.createIndicatorChart(source.view, TimeAxisMode.BAR_INDEX, indicator);
        case TRADING_RECORD_INDICATORS -> factory.createTradingRecordChart(source.view, "Capture",
                new BaseTradingRecord(), TimeAxisMode.BAR_INDEX, indicator);
        case DUAL -> factory.createDualAxisChart(source.view, indicator, "Left", indicator, "Right", "Capture",
                TimeAxisMode.BAR_INDEX);
        };
    }

    private static Indicator<Num> indexIndicator(BarSeries series, Interleaving source) {
        return new AbstractIndicator<Num>(series) {
            @Override
            public Num getValue(int index) {
                if (source != null) {
                    assertEquals(0, source.depth, "Indicator evaluation must not hold the series read lease");
                    source.mutate();
                }
                return series.numFactory().numOf(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            public String toString() {
                return "Index";
            }
        };
    }

    private static List<XYPlot> plots(JFreeChart chart) {
        return chart.getPlot() instanceof CombinedDomainXYPlot combined ? combined.getSubplots()
                : List.of(chart.getXYPlot());
    }

    private static List<String> signature(JFreeChart chart) {
        List<String> values = new ArrayList<>();
        for (XYPlot plot : plots(chart)) {
            NumberFormat axis = ((NumberAxis) plot.getDomainAxis()).getNumberFormatOverride();
            for (int i = 2; i <= 5; i++) {
                values.add(axis.format(i));
            }
            for (int i = 0; i < plot.getDatasetCount(); i++) {
                values.addAll(datasetSignature(plot, i));
            }
        }
        return values;
    }

    private static List<String> datasetSignature(XYPlot plot, int datasetIndex) {
        List<String> values = new ArrayList<>();
        XYDataset dataset = plot.getDataset(datasetIndex);
        if (dataset == null) {
            return values;
        }
        for (int s = 0; s < dataset.getSeriesCount(); s++) {
            for (int i = 0; i < dataset.getItemCount(s); i++) {
                values.add(dataset.getX(s, i) + ":" + dataset.getY(s, i));
                if (dataset instanceof OHLCDataset ohlc) {
                    values.add(ohlc.getOpen(s, i) + ":" + ohlc.getHigh(s, i) + ":" + ohlc.getLow(s, i) + ":"
                            + ohlc.getClose(s, i) + ":" + ohlc.getVolume(s, i));
                }
                XYToolTipGenerator tooltip = plot.getRenderer(datasetIndex).getToolTipGenerator(s, i);
                if (tooltip != null) {
                    values.add(tooltip.generateToolTip(dataset, s, i));
                }
            }
        }
        return values;
    }

    private static ConcurrentBarSeries series() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName("Capture").withMaxBarCount(4).build();
        for (int i = 0; i < 6; i++) {
            addBar(series, i);
        }
        return series;
    }

    private static void addBar(ConcurrentBarSeries series, int index) {
        series.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(ORIGIN.plus(Duration.ofDays(index)))
                .openPrice(99 + index)
                .highPrice(102 + index)
                .lowPrice(98 + index)
                .closePrice(100 + index)
                .volume(10)
                .add();
    }

    /**
     * Mutates after the first unlocked bar read, or immediately after a read lease.
     */
    private static final class Interleaving {
        private final ConcurrentBarSeries source = series();
        private final Mutation mutation;
        private final BarSeries view;
        private boolean armed;
        private int depth;

        private Interleaving(Mutation mutation) {
            this.mutation = mutation;
            view = (BarSeries) Proxy.newProxyInstance(BarSeries.class.getClassLoader(),
                    new Class<?>[] { BarSeries.class }, (proxy, method, args) -> {
                        if (method.getName().equals("equals")) {
                            return proxy == args[0];
                        }
                        if (method.getName().equals("withReadLock")) {
                            depth++;
                            try {
                                return source.withReadLock(() -> {
                                    if (args[0] instanceof Supplier<?> supplier) {
                                        return supplier.get();
                                    }
                                    ((Runnable) args[0]).run();
                                    return null;
                                });
                            } finally {
                                depth--;
                                mutate();
                            }
                        }
                        try {
                            Object value = method.invoke(source, args);
                            if (method.getName().equals("getBar")) {
                                mutate();
                            }
                            return value;
                        } catch (InvocationTargetException e) {
                            throw e.getCause();
                        }
                    });
        }

        private void mutate() {
            if (armed && depth == 0) {
                armed = false;
                if (mutation == Mutation.CLEAR) {
                    source.clear();
                } else {
                    addBar(source, 6);
                }
            }
        }
    }
}
