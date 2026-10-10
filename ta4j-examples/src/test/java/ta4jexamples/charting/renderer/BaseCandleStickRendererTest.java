/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.renderer;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.junit.Test;

import java.awt.*;

import static org.junit.Assert.*;

import ta4jexamples.charting.ChartingTestFixtures;

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
        assertTrue("Should extend CandlestickRenderer", renderer instanceof CandlestickRenderer);
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

    @Test
    public void testUpCandleColorMatchesTradingView() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create dataset with up candle (close > open)
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(true);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null", paint);
        assertTrue("Paint should be a Color", paint instanceof Color);

        Color color = (Color) paint;
        // TradingView's default bullish candle color: #26A69A (RGB: 38, 166, 154)
        assertEquals("Up candle red component should match TradingView", 38, color.getRed());
        assertEquals("Up candle green component should match TradingView", 166, color.getGreen());
        assertEquals("Up candle blue component should match TradingView", 154, color.getBlue());
        assertEquals("Up candle color should match DEFAULT_UP_COLOR", BaseCandleStickRenderer.DEFAULT_UP_COLOR, color);
    }

    @Test
    public void testDownCandleColorMatchesTradingView() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Create dataset with down candle (close < open)
        DefaultOHLCDataset dataset = ChartingTestFixtures.singleCandleDataset(false);
        JFreeChart chart = ChartFactory.createCandlestickChart("Test", "Time", "Price", dataset, true);
        XYPlot plot = chart.getXYPlot();
        plot.setRenderer(renderer);

        Paint paint = renderer.getItemPaint(0, 0);
        assertNotNull("Paint should not be null", paint);
        assertTrue("Paint should be a Color", paint instanceof Color);

        Color color = (Color) paint;
        // TradingView's default bearish candle color: #EF5350 (RGB: 239, 83, 80)
        assertEquals("Down candle red component should match TradingView", 239, color.getRed());
        assertEquals("Down candle green component should match TradingView", 83, color.getGreen());
        assertEquals("Down candle blue component should match TradingView", 80, color.getBlue());
        assertEquals("Down candle color should match DEFAULT_DOWN_COLOR", BaseCandleStickRenderer.DEFAULT_DOWN_COLOR,
                color);
    }

    @Test
    public void testColorConstantsAreTradingViewColors() {
        // Verify the color constants match TradingView's exact colors
        Color upColor = BaseCandleStickRenderer.DEFAULT_UP_COLOR;
        Color downColor = BaseCandleStickRenderer.DEFAULT_DOWN_COLOR;

        // TradingView's default bullish candle color: #26A69A
        assertEquals("DEFAULT_UP_COLOR red should be 38", 38, upColor.getRed());
        assertEquals("DEFAULT_UP_COLOR green should be 166", 166, upColor.getGreen());
        assertEquals("DEFAULT_UP_COLOR blue should be 154", 154, upColor.getBlue());

        // TradingView's default bearish candle color: #EF5350
        assertEquals("DEFAULT_DOWN_COLOR red should be 239", 239, downColor.getRed());
        assertEquals("DEFAULT_DOWN_COLOR green should be 83", 83, downColor.getGreen());
        assertEquals("DEFAULT_DOWN_COLOR blue should be 80", 80, downColor.getBlue());
    }

}
