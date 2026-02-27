/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.confluence;

import java.awt.Color;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.ta4j.core.BarSeries;
import org.ta4j.core.analysis.confluence.ConfluenceReport;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;

import ta4jexamples.charting.builder.ChartBuilder;
import ta4jexamples.charting.builder.ChartPlan;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Adapts {@link ConfluenceReport} outputs to the charting workflow for quick
 * visual inspection.
 *
 * @since 0.22.3
 */
public final class ConfluenceChartAdapter {

    private static final Color SMA20_COLOR = new Color(0x3A7BD5);
    private static final Color SMA50_COLOR = new Color(0x2FA36B);
    private static final Color SMA200_COLOR = new Color(0xF39C12);
    private static final Color EMA21_COLOR = new Color(0x5D5D5D);
    private static final Color SUPPORT_COLOR = new Color(0x2E8B57);
    private static final Color RESISTANCE_COLOR = new Color(0xD35454);

    private final ChartWorkflow chartWorkflow;

    /**
     * Constructs the adapter around a chart workflow facade.
     *
     * @param chartWorkflow chart workflow dependency
     * @since 0.22.3
     */
    public ConfluenceChartAdapter(ChartWorkflow chartWorkflow) {
        this.chartWorkflow = Objects.requireNonNull(chartWorkflow, "chartWorkflow cannot be null");
    }

    /**
     * Builds a chart plan for confluence review.
     *
     * @param series input series
     * @param report confluence report
     * @param title  chart title
     * @return immutable chart plan
     * @since 0.22.3
     */
    public ChartPlan buildPlan(BarSeries series, ConfluenceReport report, String title) {
        Objects.requireNonNull(series, "series cannot be null");
        Objects.requireNonNull(report, "report cannot be null");
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("title cannot be null or blank");
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma20 = new SMAIndicator(close, 20);
        SMAIndicator sma50 = new SMAIndicator(close, 50);
        SMAIndicator sma200 = new SMAIndicator(close, 200);
        EMAIndicator ema21 = new EMAIndicator(close, 21);
        RSIIndicator rsi14 = new RSIIndicator(close, 14);
        NumericIndicator macdHistogram = new MACDIndicator(close, 12, 26).getHistogram(9);

        ChartBuilder.ChartStage stage = chartWorkflow.builder().withTitle(title).withSeries(series);

        stage = stage.withIndicatorOverlay(sma20).withLineColor(SMA20_COLOR).withLabel("SMA20");
        stage = stage.withIndicatorOverlay(sma50).withLineColor(SMA50_COLOR).withLabel("SMA50");
        stage = stage.withIndicatorOverlay(sma200).withLineColor(SMA200_COLOR).withLabel("SMA200");
        stage = stage.withIndicatorOverlay(ema21).withLineColor(EMA21_COLOR).withLabel("EMA21");

        for (ConfluenceReport.LevelConfidence level : report.topSupports(3)) {
            stage = stage.withHorizontalMarker(level.level())
                    .withLineColor(SUPPORT_COLOR)
                    .withLineWidth(1.1f)
                    .withOpacity(0.70f);
        }
        for (ConfluenceReport.LevelConfidence level : report.topResistances(3)) {
            stage = stage.withHorizontalMarker(level.level())
                    .withLineColor(RESISTANCE_COLOR)
                    .withLineWidth(1.1f)
                    .withOpacity(0.70f);
        }

        stage = stage.withSubChart(rsi14)
                .withHorizontalMarker(70.0d)
                .withLineColor(RESISTANCE_COLOR)
                .withLineWidth(0.9f)
                .withOpacity(0.45f)
                .withHorizontalMarker(50.0d)
                .withLineColor(new Color(0x9E9E9E))
                .withLineWidth(0.8f)
                .withOpacity(0.35f)
                .withHorizontalMarker(30.0d)
                .withLineColor(SUPPORT_COLOR)
                .withLineWidth(0.9f)
                .withOpacity(0.45f);

        stage = stage.withSubChart(macdHistogram)
                .withHorizontalMarker(0.0d)
                .withLineColor(new Color(0x9E9E9E))
                .withLineWidth(0.8f)
                .withOpacity(0.35f);

        return stage.toPlan();
    }

    /**
     * Saves a generated confluence chart image.
     *
     * @param series    input series
     * @param report    confluence report
     * @param title     chart title
     * @param directory output directory
     * @param filename  output filename
     * @return optional saved path
     * @since 0.22.3
     */
    public Optional<Path> save(BarSeries series, ConfluenceReport report, String title, Path directory,
            String filename) {
        Objects.requireNonNull(directory, "directory cannot be null");
        if (filename == null || filename.isBlank()) {
            throw new IllegalArgumentException("filename cannot be null or blank");
        }
        ChartPlan plan = buildPlan(series, report, title);
        return chartWorkflow.save(plan, directory, filename);
    }
}
