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
package ta4jexamples.charting.renderer;

import org.jfree.chart.renderer.xy.CandlestickRenderer;
import org.jfree.data.xy.OHLCDataset;
import org.jfree.data.xy.XYDataset;

import java.awt.*;

/**
 * Custom candlestick renderer that provides distinct colors for up and down
 * candles.
 *
 * <p>
 * This renderer extends the standard JFreeChart CandlestickRenderer to provide
 * custom coloring based on whether a candle represents a price increase (up) or
 * decrease (down). Up candles are colored green, down candles are colored red.
 * </p>
 *
 * @see CandlestickRenderer
 * @since 0.19
 */
public class BaseCandleStickRenderer extends CandlestickRenderer {

    /**
     * Color for up candles (close > open). Matches TradingView's default bullish
     * candle color.
     *
     * @since 0.19
     */
    public static final Color DEFAULT_UP_COLOR = new Color(0x26A69A);

    /**
     * Color for down candles (close {@literal <} open). Matches TradingView's
     * default bearish candle color.
     *
     * @since 0.19
     */
    public static final Color DEFAULT_DOWN_COLOR = new Color(0xEF5350);

    /**
     * Returns the paint color for the specified item based on whether it's an up or
     * down candle.
     *
     * @param row    the row (series) index
     * @param column the column (item) index
     * @return the paint color for the item
     * @since 0.19
     */
    @Override
    public Paint getItemPaint(int row, int column) {
        XYDataset dataset = getPlot().getDataset();
        if (!(dataset instanceof OHLCDataset highLowData)) {
            return super.getItemPaint(row, column);
        }

        // Check for valid indices
        if (row < 0 || row >= highLowData.getSeriesCount() || column < 0 || column >= highLowData.getItemCount(row)) {
            return new Color(128, 128, 128); // Return neutral gray for invalid indices
        }

        Number yOpen = highLowData.getOpen(row, column);
        Number yClose = highLowData.getClose(row, column);

        if (yOpen == null || yClose == null) {
            return super.getItemPaint(row, column);
        }

        boolean isUpCandle = yClose.doubleValue() > yOpen.doubleValue();

        return isUpCandle ? DEFAULT_UP_COLOR : DEFAULT_DOWN_COLOR;
    }
}
