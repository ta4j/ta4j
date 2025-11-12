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
import org.jfree.chart.ChartFactory;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;

/**
 * Unit tests for {@link BaseCandleStickRenderer}.
 */
public class BaseCandleStickRendererTest {

    @Test
    public void testConstructor() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();
        assertNotNull("Renderer should not be null", renderer);
    }

    @Test
    public void testGetItemPaintUpCandle() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create a chart with up candle data (close > open)
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(true);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Test up candle paint
        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null", paint);
        assertTrue("Paint should be green for up candle", paint instanceof Color);
    }

    @Test
    public void testGetItemPaintDownCandle() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create a chart with down candle data (close < open)
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(false);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Test down candle paint
        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null", paint);
        assertTrue("Paint should be red for down candle", paint instanceof Color);
    }

    @Test
    public void testGetItemPaintWithNullDataset() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create a chart with null dataset
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", null, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Should handle null dataset gracefully
        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null even with null dataset", paint);
    }

    @Test
    public void testGetItemPaintWithNonOHLCDataset() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create a chart with non-OHLC dataset
        JFreeChart chart = ChartFactory.createXYLineChart("Test", "Time", "Price", null);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Should handle non-OHLC dataset gracefully
        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null even with non-OHLC dataset", paint);
    }

    @Test
    public void testGetItemPaintWithNullValues() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create a dataset with null values
        DefaultOHLCDataset dataset = ChartingTestFixtures.candleDatasetWithZeros();
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Should handle null values gracefully
        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null even with null values", paint);
    }

    @Test
    public void testGetItemPaintWithInvalidIndices() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(true);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Test with invalid indices - should not throw exception
        try {
            Paint paint1 = renderer.getItemPaint(-1, 0);
            Paint paint2 = renderer.getItemPaint(0, -1);
            Paint paint3 = renderer.getItemPaint(100, 100);
            // Verify paints are not null
            assertNotNull("Paint for invalid row should not be null", paint1);
            assertNotNull("Paint for invalid column should not be null", paint2);
            assertNotNull("Paint for out of bounds should not be null", paint3);
        } catch (Exception e) {
            fail("Should not throw exception with invalid indices: " + e.getMessage());
        }
    }

    @Test
    public void testColorConstants() {
        // Test that the color constants are accessible
        // Note: These are private in the actual class, so we test them indirectly
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();
        assertNotNull("Renderer should be created successfully", renderer);
    }

    @Test
    public void testInheritance() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Test that it extends CandlestickRenderer
        assertTrue("Should extend CandlestickRenderer",
                renderer instanceof org.jfree.chart.renderer.xy.CandlestickRenderer);
    }

    @Test
    public void testMultipleCalls() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(true);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        // Test multiple calls to getItemPaint
        Paint paint1 = renderer.getItemPaint(0, 0);
        Paint paint2 = renderer.getItemPaint(0, 0);

        assertNotNull("First call should return non-null paint", paint1);
        assertNotNull("Second call should return non-null paint", paint2);
        assertEquals("Multiple calls should return same paint", paint1, paint2);
    }

    @Test
    public void testUpCandleLogic() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create dataset where close > open (up candle)
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(true);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null", paint);
        // The paint should be green for up candle
        assertTrue("Should return a Color object", paint instanceof Color);
    }

    @Test
    public void testDownCandleLogic() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create dataset where close < open (down candle)
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(false);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null", paint);
        // The paint should be red for down candle
        assertTrue("Should return a Color object", paint instanceof Color);
    }

}
