/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.replay;

import java.nio.file.Path;
import java.util.Optional;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CombinedDomainXYPlot;
import org.jfree.chart.plot.Plot;
import org.jfree.chart.plot.XYPlot;

import ta4jexamples.charting.builder.ChartBuilder.ChartStage;
import ta4jexamples.charting.replay.ReplayChartModel.Layer;
import ta4jexamples.charting.workflow.ChartWorkflow;

/**
 * Draws a {@link ReplayChartModel} through the existing {@link ChartWorkflow}
 * so the replay looks and exports like every other ta4j example chart.
 */
final class ReplayFrameRenderer {

    /** Default export width in pixels. */
    static final int EXPORT_WIDTH = 1600;
    /** Default export height in pixels. */
    static final int EXPORT_HEIGHT = 900;

    private ReplayFrameRenderer() {
    }

    /**
     * Builds the chart; the price axis is fixed to the bounds computed from the
     * visible window so equal frames render equal axes.
     *
     * @param workflow the chart workflow
     * @param model    the model
     * @return the chart
     */
    static JFreeChart chart(final ChartWorkflow workflow, final ReplayChartModel model) {
        ChartStage stage = workflow.builder().withTitle(model.title()).withSeries(model.series());
        for (final Layer layer : model.layers()) {
            if (layer.labels().isEmpty()) {
                continue;
            }
            if (layer.line() != null) {
                stage = stage.withIndicatorOverlay(layer.line())
                        .withLineColor(layer.color())
                        .withLineWidth(layer.width())
                        .withConnectAcrossNaN(true)
                        .withOpacity(0.85f)
                        .withLabel(layer.name());
            }
            stage = stage.withIndicatorOverlay(layer.indicator())
                    .withLineColor(layer.color())
                    .withLineWidth(layer.width())
                    .withOpacity(1.0f)
                    .withLabel(layer.line() == null ? layer.name() : layer.name() + " points");
        }
        final JFreeChart chart = stage.toChart();
        priceAxis(chart.getPlot()).setRange(model.axisLow(), model.axisHigh());
        return chart;
    }

    private static NumberAxis priceAxis(final Plot plot) {
        final XYPlot xy = plot instanceof CombinedDomainXYPlot combined ? (XYPlot) combined.getSubplots().getFirst()
                : (XYPlot) plot;
        return (NumberAxis) xy.getRangeAxis();
    }

    /**
     * Writes the chart image to {@code directory}.
     *
     * @param chart     the chart
     * @param model     the model the chart was built from
     * @param directory target directory
     * @param filename  file name without extension
     * @return the written file
     */
    static Optional<Path> save(final JFreeChart chart, final ReplayChartModel model, final Path directory,
            final String filename) {
        return new ChartWorkflow(directory.toString()).saveChartImage(chart, model.series(), filename, EXPORT_WIDTH,
                EXPORT_HEIGHT);
    }
}
