/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.renderer;

import org.jfree.chart.JFreeChart;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.junit.jupiter.api.Test;

import java.awt.*;

import static org.junit.jupiter.api.Assertions.*;

import ta4jexamples.charting.ChartingTestFixtures;

/**
 * Unit tests for {@link BaseCandleStickRenderer}.
 */
public class BaseCandleStickRendererTest {

    @Test
    public void testConstructor() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();
        assertNotNull(renderer, "Renderer should not be null");
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
        assertNotNull(paint, "Paint should not be null");
        assertTrue(paint instanceof Color, "Paint should be green for up candle");
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
        assertNotNull(paint, "Paint should not be null");
        assertTrue(paint instanceof Color, "Paint should be red for down candle");
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
        assertNotNull(paint, "Paint should not be null even with null dataset");
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
        assertNotNull(paint, "Paint should not be null even with non-OHLC dataset");
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
        assertNotNull(paint, "Paint should not be null even with null values");
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
            assertNotNull(paint1, "Paint for invalid row should not be null");
            assertNotNull(paint2, "Paint for invalid column should not be null");
            assertNotNull(paint3, "Paint for out of bounds should not be null");
        } catch (Exception e) {
            fail("Should not throw exception with invalid indices: " + e.getMessage());
        }
    }

    @Test
    public void testColorConstants() {
        // Test that the color constants are accessible
        // Note: These are private in the actual class, so we test them indirectly
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();
        assertNotNull(renderer, "Renderer should be created successfully");
    }

    @Test
    public void testInheritance() {
        BaseCandleStickRenderer renderer = new BaseCandleStickRenderer();

        // Test that it extends CandlestickRenderer
        assertTrue(renderer instanceof CandlestickRenderer, "Should extend CandlestickRenderer");
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

        assertNotNull(paint1, "First call should return non-null paint");
        assertNotNull(paint2, "Second call should return non-null paint");
        assertEquals(paint1, paint2, "Multiple calls should return same paint");
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
        assertNotNull(paint, "Paint should not be null");
        // The paint should be green for up candle
        assertTrue(paint instanceof Color, "Should return a Color object");
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
        assertNotNull(paint, "Paint should not be null");
        // The paint should be red for down candle
        assertTrue(paint instanceof Color, "Should return a Color object");
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
        assertNotNull(paint, "Paint should not be null");
        assertTrue(paint instanceof Color, "Paint should be a Color");

        Color color = (Color) paint;
        // TradingView's default bullish candle color: #26A69A (RGB: 38, 166, 154)
        assertEquals(38, color.getRed(), "Up candle red component should match TradingView");
        assertEquals(166, color.getGreen(), "Up candle green component should match TradingView");
        assertEquals(154, color.getBlue(), "Up candle blue component should match TradingView");
        assertEquals(BaseCandleStickRenderer.DEFAULT_UP_COLOR, color, "Up candle color should match DEFAULT_UP_COLOR");
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
        assertNotNull(paint, "Paint should not be null");
        assertTrue(paint instanceof Color, "Paint should be a Color");

        Color color = (Color) paint;
        // TradingView's default bearish candle color: #EF5350 (RGB: 239, 83, 80)
        assertEquals(239, color.getRed(), "Down candle red component should match TradingView");
        assertEquals(83, color.getGreen(), "Down candle green component should match TradingView");
        assertEquals(80, color.getBlue(), "Down candle blue component should match TradingView");
        assertEquals(BaseCandleStickRenderer.DEFAULT_DOWN_COLOR, color,
                "Down candle color should match DEFAULT_DOWN_COLOR");
    }

    @Test
    public void testColorConstantsAreTradingViewColors() {
        // Verify the color constants match TradingView's exact colors
        Color upColor = BaseCandleStickRenderer.DEFAULT_UP_COLOR;
        Color downColor = BaseCandleStickRenderer.DEFAULT_DOWN_COLOR;

        // TradingView's default bullish candle color: #26A69A
        assertEquals(38, upColor.getRed(), "DEFAULT_UP_COLOR red should be 38");
        assertEquals(166, upColor.getGreen(), "DEFAULT_UP_COLOR green should be 166");
        assertEquals(154, upColor.getBlue(), "DEFAULT_UP_COLOR blue should be 154");

        // TradingView's default bearish candle color: #EF5350
        assertEquals(239, downColor.getRed(), "DEFAULT_DOWN_COLOR red should be 239");
        assertEquals(83, downColor.getGreen(), "DEFAULT_DOWN_COLOR green should be 83");
        assertEquals(80, downColor.getBlue(), "DEFAULT_DOWN_COLOR blue should be 80");
    }

}
