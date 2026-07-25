/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.compose;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.annotations.XYTextAnnotation;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.axis.NumberTickUnit;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.LookupPaintScale;
import org.jfree.chart.renderer.xy.XYBlockRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.chart.title.PaintScaleLegend;
import org.jfree.chart.ui.RectangleAnchor;
import org.jfree.chart.ui.RectangleEdge;
import org.jfree.chart.ui.RectangleInsets;
import org.jfree.data.xy.DefaultXYZDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;
import org.ta4j.core.portfolio.PortfolioCorrelations.ClusterMerge;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationHierarchy;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationMatrix;

/**
 * Creates annotated heatmaps and headless-safe dendrograms for portfolio
 * correlation results.
 *
 * @since 0.23.1
 */
public final class PortfolioCorrelationChartFactory {

    private static final Color BACKGROUND = new Color(0xFAFAF8);
    private static final Color GRID = new Color(0xD9D9D4);
    private static final Color LINE = new Color(0x333333);
    private static final Font ANNOTATION_FONT = new Font(Font.SANS_SERIF, Font.BOLD, 11);

    /**
     * Creates an annotated correlation heatmap.
     *
     * @param title  chart title
     * @param matrix finite correlation matrix
     * @return heatmap chart
     */
    public JFreeChart createHeatmap(String title, CorrelationMatrix matrix) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(matrix, "matrix");
        List<String> assets = matrix.getAssets();
        int assetCount = assets.size();
        double[][] data = new double[3][assetCount * assetCount];
        int item = 0;
        for (int row = 0; row < assetCount; row++) {
            for (int column = 0; column < assetCount; column++) {
                double coefficient = matrix.getCoefficient(assets.get(row), assets.get(column)).doubleValue();
                if (!Double.isFinite(coefficient)) {
                    throw new IllegalArgumentException("heatmaps require finite correlation coefficients");
                }
                data[0][item] = column;
                data[1][item] = row;
                data[2][item] = coefficient;
                item++;
            }
        }

        DefaultXYZDataset dataset = new DefaultXYZDataset();
        dataset.addSeries("Correlation", data);
        LookupPaintScale paintScale = correlationPaintScale();
        XYBlockRenderer renderer = new XYBlockRenderer();
        renderer.setBlockWidth(1.0);
        renderer.setBlockHeight(1.0);
        renderer.setBlockAnchor(RectangleAnchor.CENTER);
        renderer.setPaintScale(paintScale);

        SymbolAxis domainAxis = symbolAxis(assets, true);
        SymbolAxis rangeAxis = symbolAxis(assets, false);
        rangeAxis.setInverted(true);
        XYPlot plot = new XYPlot(dataset, domainAxis, rangeAxis, renderer);
        stylePlot(plot);
        for (int row = 0; row < assetCount; row++) {
            for (int column = 0; column < assetCount; column++) {
                double coefficient = data[2][row * assetCount + column];
                XYTextAnnotation annotation = new XYTextAnnotation(String.format(Locale.ROOT, "%.2f", coefficient),
                        column, row);
                annotation.setFont(ANNOTATION_FONT);
                annotation.setPaint(Math.abs(coefficient) >= 0.55 ? Color.WHITE : LINE);
                plot.addAnnotation(annotation);
            }
        }

        JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        chart.setBackgroundPaint(BACKGROUND);
        NumberAxis legendAxis = new NumberAxis("Correlation");
        legendAxis.setRange(-1.0, 1.0);
        legendAxis.setTickUnit(new NumberTickUnit(0.25));
        PaintScaleLegend legend = new PaintScaleLegend(paintScale, legendAxis);
        legend.setPosition(RectangleEdge.RIGHT);
        legend.setMargin(new RectangleInsets(4, 8, 4, 4));
        chart.addSubtitle(legend);
        return chart;
    }

    /**
     * Creates a complete-linkage dendrogram.
     *
     * @param title     chart title
     * @param hierarchy complete-linkage hierarchy
     * @return dendrogram chart
     */
    public JFreeChart createDendrogram(String title, CorrelationHierarchy hierarchy) {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(hierarchy, "hierarchy");
        List<String> assets = hierarchy.getAssets();
        int assetCount = assets.size();
        double[] clusterX = new double[assetCount * 2 - 1];
        double[] clusterHeight = new double[assetCount * 2 - 1];
        Map<String, Integer> leafPositions = new LinkedHashMap<>();
        for (int position = 0; position < hierarchy.getLeafOrder().size(); position++) {
            leafPositions.put(hierarchy.getLeafOrder().get(position), position);
        }
        for (int index = 0; index < assetCount; index++) {
            clusterX[index] = leafPositions.get(assets.get(index));
        }

        XYSeriesCollection dataset = new XYSeriesCollection();
        for (int mergeIndex = 0; mergeIndex < hierarchy.getMerges().size(); mergeIndex++) {
            ClusterMerge merge = hierarchy.getMerges().get(mergeIndex);
            double leftX = clusterX[merge.getLeftClusterIndex()];
            double rightX = clusterX[merge.getRightClusterIndex()];
            double height = merge.getDistance().doubleValue();
            XYSeries branch = new XYSeries("Merge " + (mergeIndex + 1), false, true);
            branch.add(leftX, clusterHeight[merge.getLeftClusterIndex()]);
            branch.add(leftX, height);
            branch.add(rightX, height);
            branch.add(rightX, clusterHeight[merge.getRightClusterIndex()]);
            dataset.addSeries(branch);

            int clusterIndex = assetCount + mergeIndex;
            clusterX[clusterIndex] = (leftX + rightX) / 2.0;
            clusterHeight[clusterIndex] = height;
        }

        SymbolAxis domainAxis = symbolAxis(hierarchy.getLeafOrder(), false);
        NumberAxis rangeAxis = new NumberAxis("Complete-linkage distance");
        rangeAxis.setAutoRangeIncludesZero(true);
        XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer(true, false);
        for (int index = 0; index < dataset.getSeriesCount(); index++) {
            renderer.setSeriesPaint(index, LINE);
            renderer.setSeriesStroke(index, new BasicStroke(2.0f));
        }
        XYPlot plot = new XYPlot(dataset, domainAxis, rangeAxis, renderer);
        stylePlot(plot);
        JFreeChart chart = new JFreeChart(title, JFreeChart.DEFAULT_TITLE_FONT, plot, false);
        chart.setBackgroundPaint(BACKGROUND);
        return chart;
    }

    private static SymbolAxis symbolAxis(List<String> symbols, boolean verticalLabels) {
        SymbolAxis axis = new SymbolAxis("", symbols.toArray(String[]::new));
        axis.setRange(-0.5, symbols.size() - 0.5);
        axis.setGridBandsVisible(false);
        axis.setVerticalTickLabels(verticalLabels);
        return axis;
    }

    private static void stylePlot(XYPlot plot) {
        plot.setBackgroundPaint(BACKGROUND);
        plot.setDomainGridlinePaint(GRID);
        plot.setRangeGridlinePaint(GRID);
        plot.setOutlinePaint(GRID);
    }

    private static LookupPaintScale correlationPaintScale() {
        LookupPaintScale scale = new LookupPaintScale(-1.0, 1.0, BACKGROUND);
        for (int step = 0; step <= 20; step++) {
            double value = -1.0 + step / 10.0;
            double normalized = (value + 1.0) / 2.0;
            Color color;
            if (normalized < 0.5) {
                double fraction = normalized * 2.0;
                color = interpolate(new Color(0x2166AC), Color.WHITE, fraction);
            } else {
                double fraction = (normalized - 0.5) * 2.0;
                color = interpolate(Color.WHITE, new Color(0xB2182B), fraction);
            }
            scale.add(value, color);
        }
        return scale;
    }

    private static Color interpolate(Color start, Color end, double fraction) {
        int red = (int) Math.round(start.getRed() + fraction * (end.getRed() - start.getRed()));
        int green = (int) Math.round(start.getGreen() + fraction * (end.getGreen() - start.getGreen()));
        int blue = (int) Math.round(start.getBlue() + fraction * (end.getBlue() - start.getBlue()));
        return new Color(red, green, blue);
    }
}
