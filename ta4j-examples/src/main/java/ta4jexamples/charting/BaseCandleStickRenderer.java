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
 */
public class BaseCandleStickRenderer extends CandlestickRenderer {

    /** Color for up candles (close > open). */
    private static final Color UP_OUTLINE_COLOR = new Color(0x00ff00);

    /** Color for down candles (close < open). */
    private static final Color DOWN_OUTLINE_COLOR = new Color(0xff0000);

    /**
     * Returns the paint color for the specified item based on whether it's an up or
     * down candle.
     *
     * @param row    the row (series) index
     * @param column the column (item) index
     * @return the paint color for the item
     */
    @Override
    public Paint getItemPaint(int row, int column) {
        XYDataset dataset = getPlot().getDataset();
        if (!(dataset instanceof OHLCDataset)) {
            return super.getItemPaint(row, column);
        }

        OHLCDataset highLowData = (OHLCDataset) dataset;
        int series = row;
        int item = column;

        // Check for valid indices
        if (series < 0 || series >= highLowData.getSeriesCount() || item < 0
                || item >= highLowData.getItemCount(series)) {
            return new Color(128, 128, 128); // Return neutral gray for invalid indices
        }

        Number yOpen = highLowData.getOpen(series, item);
        Number yClose = highLowData.getClose(series, item);

        if (yOpen == null || yClose == null) {
            return super.getItemPaint(row, column);
        }

        boolean isUpCandle = yClose.doubleValue() > yOpen.doubleValue();

        return isUpCandle ? UP_OUTLINE_COLOR : DOWN_OUTLINE_COLOR;
    }
}
