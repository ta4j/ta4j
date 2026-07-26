/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.compose;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.SymbolAxis;
import org.jfree.chart.plot.XYPlot;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.portfolio.PortfolioCorrelations;
import org.ta4j.core.portfolio.PortfolioCorrelations.CorrelationMatrix;
import org.ta4j.core.portfolio.PortfolioSeries;

public class PortfolioCorrelationChartFactoryTest {

    @Test
    public void rendersAnnotatedHeatmapWithAssetLabels() {
        CorrelationMatrix matrix = matrix();
        PortfolioCorrelationChartFactory factory = new PortfolioCorrelationChartFactory();

        JFreeChart chart = factory.createHeatmap("Return correlations", matrix);
        BufferedImage image = chart.createBufferedImage(800, 600);
        XYPlot plot = chart.getXYPlot();

        assertEquals(800, image.getWidth());
        assertEquals(600, image.getHeight());
        assertEquals(9, plot.getAnnotations().size());
        assertArrayEquals(new String[] { "ALPHA", "BETA", "GAMMA" }, ((SymbolAxis) plot.getDomainAxis()).getSymbols());
        assertTrue(countNonBackgroundPixels(image) > 10_000);
    }

    @Test
    public void rendersCompleteLinkageDendrogramWithEveryMergeAndLeaf() {
        CorrelationMatrix matrix = matrix();
        PortfolioCorrelationChartFactory factory = new PortfolioCorrelationChartFactory();

        JFreeChart chart = factory.createDendrogram("Correlation hierarchy", matrix.completeLinkage());
        BufferedImage image = chart.createBufferedImage(800, 600);
        XYPlot plot = chart.getXYPlot();

        assertEquals(2, plot.getDataset().getSeriesCount());
        assertArrayEquals(matrix.completeLinkage().getLeafOrder().toArray(String[]::new),
                ((SymbolAxis) plot.getDomainAxis()).getSymbols());
        assertTrue(countNonBackgroundPixels(image) > 5_000);
    }

    private static CorrelationMatrix matrix() {
        PortfolioSeries series = new PortfolioSeries(series("ALPHA", 100, 110, 99, 120, 115),
                series("BETA", 100, 105, 101, 111, 109), series("GAMMA", 100, 95, 101, 90, 92));
        return new PortfolioCorrelations(series).getSimpleReturnMatrix();
    }

    private static BarSeries series(String name, double... closes) {
        BarSeries series = new BaseBarSeriesBuilder().withName(name).build();
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        Num zero = series.numFactory().zero();
        for (int index = 0; index < closes.length; index++) {
            Num close = series.numFactory().numOf(closes[index]);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(zero)
                    .add();
        }
        return series;
    }

    private static int countNonBackgroundPixels(BufferedImage image) {
        int background = image.getRGB(0, 0);
        int count = 0;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) != background) {
                    count++;
                }
            }
        }
        return count;
    }
}
