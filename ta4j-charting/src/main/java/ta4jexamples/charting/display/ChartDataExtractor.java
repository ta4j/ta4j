/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.display;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.DefaultOHLCDataset;
import org.jfree.data.xy.XYDataset;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Extracts formatted text data from chart datasets for display in mouseover
 * tooltips.
 * <p>
 * This class handles different dataset types (OHLC, TimeSeries, XYSeries) and
 * formats them appropriately for display. It is designed to be testable
 * independently of the chart display infrastructure.
 *
 * @since 0.19
 */
public final class ChartDataExtractor {

    private static final Logger LOG = LogManager.getLogger(ChartDataExtractor.class);

    private static final String DATE_FORMAT = "yyyy-MM-dd HH:mm:ss";

    /**
     * Extracts formatted text representation of data from a chart dataset at the
     * specified series and item indices.
     *
     * @param dataset     the dataset to extract data from
     * @param seriesIndex the index of the series within the dataset
     * @param itemIndex   the index of the item within the series
     * @return formatted text string, or null if extraction fails or data is not
     *         available
     */
    public String extractDataText(XYDataset dataset, int seriesIndex, int itemIndex) {
        if (dataset instanceof DefaultOHLCDataset ohlcDataset) {
            return extractOHLCData(ohlcDataset, seriesIndex, itemIndex);
        } else if (dataset instanceof TimeSeriesCollection timeSeriesCollection) {
            return extractTimeSeriesData(timeSeriesCollection, seriesIndex, itemIndex);
        } else if (dataset instanceof XYSeriesCollection xyCollection) {
            return extractXYSeriesData(xyCollection, seriesIndex, itemIndex);
        }
        return null;
    }

    private String extractOHLCData(DefaultOHLCDataset dataset, int seriesIndex, int itemIndex) {
        try {
            double xValue = dataset.getXValue(seriesIndex, itemIndex);
            double open = dataset.getOpenValue(seriesIndex, itemIndex);
            double high = dataset.getHighValue(seriesIndex, itemIndex);
            double low = dataset.getLowValue(seriesIndex, itemIndex);
            double close = dataset.getCloseValue(seriesIndex, itemIndex);
            double volume = dataset.getVolumeValue(seriesIndex, itemIndex);

            DecimalFormat priceFormat = new DecimalFormat("#,##0.00###");
            SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
            return String.format("Date: %s | O: %s | H: %s | L: %s | C: %s | V: %s",
                    dateFormat.format(new Date((long) xValue)), priceFormat.format(open), priceFormat.format(high),
                    priceFormat.format(low), priceFormat.format(close), priceFormat.format(volume));
        } catch (Exception ex) {
            LOG.debug("Error extracting OHLC data", ex);
            return null;
        }
    }

    private String extractTimeSeriesData(TimeSeriesCollection dataset, int seriesIndex, int itemIndex) {
        try {
            TimeSeries timeSeries = dataset.getSeries(seriesIndex);
            if (timeSeries != null && itemIndex < timeSeries.getItemCount()) {
                org.jfree.data.time.TimeSeriesDataItem dataItem = timeSeries.getDataItem(itemIndex);
                String seriesName = timeSeries.getKey().toString();
                String dateStr = dataItem.getPeriod().toString();
                double value = dataItem.getValue().doubleValue();
                DecimalFormat valueFormat = new DecimalFormat("#,##0.00###");
                return String.format("%s: %s | Value: %s", seriesName, dateStr, valueFormat.format(value));
            }
        } catch (Exception ex) {
            LOG.debug("Error extracting TimeSeries data", ex);
        }
        return null;
    }

    private String extractXYSeriesData(XYSeriesCollection dataset, int seriesIndex, int itemIndex) {
        try {
            XYSeries series = dataset.getSeries(seriesIndex);
            if (series != null && itemIndex < series.getItemCount()) {
                double x = series.getX(itemIndex).doubleValue();
                double y = series.getY(itemIndex).doubleValue();
                SimpleDateFormat dateFormat = new SimpleDateFormat(DATE_FORMAT);
                DecimalFormat valueFormat = new DecimalFormat("#,##0.00###");
                return String.format("Date: %s | Value: %s", dateFormat.format(new Date((long) x)),
                        valueFormat.format(y));
            }
        } catch (Exception ex) {
            LOG.debug("Error extracting indicator data", ex);
        }
        return null;
    }
}
