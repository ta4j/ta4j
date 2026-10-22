/*
 * SPDX-License-Identifier: MIT
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
