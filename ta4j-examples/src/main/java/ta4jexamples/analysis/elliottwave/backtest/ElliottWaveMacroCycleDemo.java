/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.analysis.elliottwave.backtest;

import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

import org.jfree.chart.JFreeChart;
import org.ta4j.core.BarSeries;

/**
 * Generic macro-cycle demo facade for any instrument and timespan.
 *
 * <p>
 * Callers can either provide the exact {@link BarSeries} window plus an
 * {@link ElliottWaveAnchorCalibrationHarness.AnchorRegistry} that describes the
 * cycle anchors they want to validate, or let the demo infer broad macro turns
 * directly from the series. Both paths reuse the same historical study, chart
 * rendering, and JSON reporting flow as the BTC preset.
 *
 * @since 0.22.4
 */
public final class ElliottWaveMacroCycleDemo {

    private ElliottWaveMacroCycleDemo() {
    }

    /**
     * Runs the historical macro-cycle report using anchors inferred directly from
     * the supplied series.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated macro-cycle report
     * @since 0.22.4
     */
    public static ElliottWaveBtcMacroCycleDemo.DemoReport generateHistoricalReport(final BarSeries series,
            final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        return generateHistoricalReport(series, ElliottWaveMacroCycleDetector.inferAnchorRegistry(series),
                chartDirectory);
    }

    /**
     * Runs the historical macro-cycle report for the supplied series and anchor
     * registry.
     *
     * @param series         series window to analyze
     * @param registry       anchor registry describing the macro-cycle turns to
     *                       validate
     * @param chartDirectory directory for the rendered chart and JSON summary
     * @return generated macro-cycle report
     * @since 0.22.4
     */
    public static ElliottWaveBtcMacroCycleDemo.DemoReport generateHistoricalReport(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        return ElliottWaveBtcMacroCycleDemo.generateReport(series, registry, chartDirectory);
    }

    /**
     * Saves the rendered macro-cycle chart using anchors inferred directly from the
     * supplied series.
     *
     * @param series         series window to analyze
     * @param chartDirectory directory for the saved chart image
     * @return saved chart path when rendering succeeds
     * @since 0.22.4
     */
    public static Optional<Path> saveHistoricalChart(final BarSeries series, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        return saveHistoricalChart(series, ElliottWaveMacroCycleDetector.inferAnchorRegistry(series), chartDirectory);
    }

    /**
     * Saves the rendered macro-cycle chart for the supplied series and anchors.
     *
     * @param series         series window to analyze
     * @param registry       anchor registry describing the macro-cycle turns to
     *                       validate
     * @param chartDirectory directory for the saved chart image
     * @return saved chart path when rendering succeeds
     * @since 0.22.4
     */
    public static Optional<Path> saveHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry, final Path chartDirectory) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(chartDirectory, "chartDirectory");
        return ElliottWaveBtcMacroCycleDemo.saveMacroCycleChart(series, registry, chartDirectory);
    }

    /**
     * Renders the macro-cycle chart using anchors inferred directly from the
     * supplied series.
     *
     * @param series series window to analyze
     * @return rendered chart
     * @since 0.22.4
     */
    public static JFreeChart renderHistoricalChart(final BarSeries series) {
        Objects.requireNonNull(series, "series");
        return renderHistoricalChart(series, ElliottWaveMacroCycleDetector.inferAnchorRegistry(series));
    }

    /**
     * Renders the macro-cycle chart for the supplied series and anchors without
     * writing files.
     *
     * @param series   series window to analyze
     * @param registry anchor registry describing the macro-cycle turns to validate
     * @return rendered chart
     * @since 0.22.4
     */
    public static JFreeChart renderHistoricalChart(final BarSeries series,
            final ElliottWaveAnchorCalibrationHarness.AnchorRegistry registry) {
        Objects.requireNonNull(series, "series");
        Objects.requireNonNull(registry, "registry");
        return ElliottWaveBtcMacroCycleDemo.renderMacroCycleChart(series, registry);
    }
}
