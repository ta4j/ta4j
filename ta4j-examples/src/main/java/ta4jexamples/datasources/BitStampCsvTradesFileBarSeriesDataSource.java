/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.datasources;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;
import ta4jexamples.datasources.file.AbstractFileBarSeriesDataSource;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;

/**
 * This class builds a Ta4j bar series from a Bitstamp CSV file containing
 * trades. It reads trade-level data (timestamp, price, volume) and aggregates
 * them into OHLCV bars suitable for technical analysis.
 * <p>
 * Implements {@link BarSeriesDataSource} to support domain-driven loading by
 * ticker, interval, and date range. Searches for Bitstamp CSV files matching
 * the specified criteria in the classpath.
 */
public class BitStampCsvTradesFileBarSeriesDataSource extends AbstractFileBarSeriesDataSource {

    private static final Logger LOG = LogManager.getLogger(BitStampCsvTradesFileBarSeriesDataSource.class);

    private static final String DEFAULT_BITSTAMP_FILE = "Bitstamp-BTC-USD-PT5M-20131125_20131201.csv";

    /**
     * Creates a new BitStampCsvTradesFileBarSeriesDataSource with "Bitstamp" as the
     * source name.
     */
    public BitStampCsvTradesFileBarSeriesDataSource() {
        super("Bitstamp");
    }

    @Override
    protected String getFileExtension() {
        return "csv";
    }

    @Override
    protected BarSeries searchAndLoadFile(String ticker, String intervalStr, String sourcePrefix,
            String startDateTimeStr, String endDateTimeStr, String startDateStr, String endDateStr, Duration interval,
            Instant start, Instant end) {
        // Try exact pattern with interval-appropriate format:
        // {sourceName}-{ticker}-{interval}-{startDateTime}_{endDateTime}.csv
        String exactPattern = sourcePrefix + ticker.toUpperCase() + "-" + intervalStr + "-" + startDateTimeStr + "_"
                + endDateTimeStr + ".csv";
        BarSeries series = loadBitstampSeries(exactPattern);
        if (series != null && !series.isEmpty()) {
            return filterAndAggregateSeries(series, interval, start, end);
        }

        // Fallback to date-only format for backward compatibility with existing files
        String exactPatternDateOnly = sourcePrefix + ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_"
                + endDateStr + ".csv";
        series = loadBitstampSeries(exactPatternDateOnly);
        if (series != null && !series.isEmpty()) {
            return filterAndAggregateSeries(series, interval, start, end);
        }

        // Try broader pattern: {sourceName}-{ticker}-*-{startDateTime}_*.csv
        String broaderPattern = sourcePrefix + ticker.toUpperCase() + "-*-" + startDateTimeStr + "_*.csv";
        series = searchAndLoadBitstampFile(broaderPattern, interval, start, end);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Fallback to date-only format for broader pattern
        String broaderPatternDateOnly = sourcePrefix + ticker.toUpperCase() + "-*-" + startDateStr + "_*.csv";
        series = searchAndLoadBitstampFile(broaderPatternDateOnly, interval, start, end);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Try even broader: {sourceName}-{ticker}-*.csv (then filter by date range)
        String broadestPattern = sourcePrefix + ticker.toUpperCase() + "-*.csv";
        series = searchAndLoadBitstampFile(broadestPattern, interval, start, end);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        return null;
    }

    @Override
    public BarSeries loadSeries(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Source cannot be null or empty");
        }
        return loadBitstampSeries(source);
    }

    /**
     * Searches for a Bitstamp CSV file matching the pattern and loads it if found.
     *
     * @param pattern  the filename pattern to search for (supports wildcards)
     * @param interval the desired bar interval
     * @param start    the start date
     * @param end      the end date
     * @return the loaded and filtered BarSeries, or null if not found
     */
    private BarSeries searchAndLoadBitstampFile(String pattern, Duration interval, Instant start, Instant end) {
        // Try direct pattern match as resource
        if (!pattern.contains("*")) {
            BarSeries series = loadBitstampSeries(pattern);
            if (series != null && !series.isEmpty()) {
                return filterAndAggregateSeries(series, interval, start, end);
            }
        }

        // For wildcard patterns, try common variations
        String[] variations = { pattern.replace("*", "PT5M"), pattern.replace("*", "PT1D") };
        for (String variation : variations) {
            BarSeries series = loadBitstampSeries(variation);
            if (series != null && !series.isEmpty()) {
                return filterAndAggregateSeries(series, interval, start, end);
            }
        }
        return null;
    }

    /**
     * Filters a series to the date range and re-aggregates with the specified
     * interval.
     *
     * @param series   the series to filter and aggregate
     * @param interval the desired bar interval
     * @param start    the start date (inclusive)
     * @param end      the end date (inclusive)
     * @return a new BarSeries with bars within the date range and specified
     *         interval, or null if no bars match
     */
    private BarSeries filterAndAggregateSeries(BarSeries series, Duration interval, Instant start, Instant end) {
        if (series == null || series.isEmpty()) {
            return null;
        }

        // Filter trades within date range and re-aggregate with new interval
        // This is a simplified implementation - in practice, you'd want to
        // re-aggregate from the original trade data
        var filteredSeries = new BaseBarSeriesBuilder().withName(series.getName()).build();
        int barsInDateRange = 0;
        int barsWithMatchingInterval = 0;
        Duration actualInterval = null;

        for (int i = 0; i < series.getBarCount(); i++) {
            var bar = series.getBar(i);
            Instant barEnd = bar.getEndTime();
            if (!barEnd.isBefore(start) && !barEnd.isAfter(end)) {
                barsInDateRange++;
                if (actualInterval == null) {
                    actualInterval = bar.getTimePeriod();
                }
                // If interval matches, add as-is; otherwise would need re-aggregation
                if (bar.getTimePeriod().equals(interval)) {
                    barsWithMatchingInterval++;
                    filteredSeries.addBar(bar);
                }
            }
        }

        // Log warning if bars exist in date range but intervals don't match
        if (barsInDateRange > 0 && barsWithMatchingInterval == 0 && actualInterval != null) {
            LOG.warn(
                    "Found {} bars within date range [{} to {}], but bar interval ({}) does not match requested interval ({}). "
                            + "Re-aggregation from original trade data is required but not implemented. Returning null.",
                    barsInDateRange, start, end, actualInterval, interval);
        }

        return filteredSeries.isEmpty() ? null : filteredSeries;
    }

    /**
     * Loads a bar series from the default Bitstamp CSV file. The method reads trade
     * data from a CSV file containing Bitstamp exchange trades and converts it into
     * a bar series format suitable for technical analysis.
     *
     * @return the bar series from Bitstamp (bitcoin exchange) trades
     */
    public static BarSeries loadBitstampSeries() {
        return loadBitstampSeries(DEFAULT_BITSTAMP_FILE);
    }

    /**
     * Loads a bar series from a specified Bitstamp CSV file. The method reads trade
     * data from a CSV file containing Bitstamp exchange trades and converts it into
     * a bar series format suitable for technical analysis.
     *
     * @param bitstampCsvFile the path to the CSV file containing Bitstamp trade
     *                        data
     * @return the bar series built from the Bitstamp trades data
     */
    public static BarSeries loadBitstampSeries(String bitstampCsvFile) {

        // Reading all lines of the CSV file
        InputStream stream = BitStampCsvTradesFileBarSeriesDataSource.class.getClassLoader()
                .getResourceAsStream(bitstampCsvFile);
        List<String[]> lines = null;
        if (stream == null) {
            LOG.debug("CSV file not found in classpath: {}", bitstampCsvFile);
            return null;
        }
        try (final var csvReader = new com.opencsv.CSVReader(new InputStreamReader(stream))) {
            lines = csvReader.readAll();
            lines.remove(0); // Removing header line
        } catch (Exception ioe) {
            LOG.error("Unable to load trades from CSV", ioe);
        }

        var series = new BaseBarSeriesBuilder().withName(bitstampCsvFile).build();
        if ((lines != null) && !lines.isEmpty()) {

            // Getting the first and last trades timestamps
            Instant beginTime = null;
            Instant endTime = null;
            try {
                beginTime = Instant.ofEpochMilli(Long.parseLong(lines.get(0)[0]) * 1000);
                endTime = Instant.ofEpochMilli(Long.parseLong(lines.get(lines.size() - 1)[0]) * 1000);
            } catch (NumberFormatException nfe) {
                LOG.error("Invalid trade timestamp format in CSV: {}", nfe.getMessage());
                return null;
            }
            if (beginTime.isAfter(endTime)) {
                beginTime = endTime;
                // Since the CSV file has the most recent trades at the top of the file, we'll
                // reverse the list to feed
                // the List<Bar> correctly.
                Collections.reverse(lines);
            }
            // build the list of populated bars (default 5-minute bars)
            buildSeries(series, beginTime, endTime, 300, lines);
        }

        return series.isEmpty() ? null : series;
    }

    /**
     * Builds a list of populated bars from csv data.
     *
     * @param beginTime the begin time of the whole period
     * @param endTime   the end time of the whole period
     * @param duration  the bar duration (in seconds)
     * @param lines     the csv data returned by CSVReader.readAll()
     */
    private static void buildSeries(BarSeries series, Instant beginTime, Instant endTime, int duration,
            List<String[]> lines) {

        Duration barDuration = Duration.ofSeconds(duration);
        Instant barEndTime = beginTime;
        ListIterator<String[]> iterator = lines.listIterator();
        // line number of trade data
        do {
            // build a bar
            barEndTime = barEndTime.plus(barDuration);
            var bar = series.barBuilder().timePeriod(barDuration).endTime(barEndTime).volume(0).amount(0).build();
            do {
                // get a trade
                String[] tradeLine = iterator.next();
                Instant tradeTimeStamp;
                try {
                    tradeTimeStamp = Instant.ofEpochMilli(Long.parseLong(tradeLine[0]) * 1000);
                } catch (NumberFormatException nfe) {
                    LOG.warn("Invalid trade timestamp format in CSV line, skipping trade: {}",
                            tradeLine.length > 0 ? tradeLine[0] : "empty line", nfe);
                    continue;
                }
                // if the trade happened during the bar
                if (bar.inPeriod(tradeTimeStamp)) {
                    // add the trade to the bar
                    Num tradePrice;
                    Num tradeVolume;
                    try {
                        tradePrice = series.numFactory().numOf(Double.parseDouble(tradeLine[1]));
                        tradeVolume = series.numFactory().numOf(Double.parseDouble(tradeLine[2]));
                    } catch (NumberFormatException nfe) {
                        LOG.warn(
                                "Invalid trade price or volume format in CSV line, skipping trade: price={}, volume={}",
                                tradeLine.length > 1 ? tradeLine[1] : "missing",
                                tradeLine.length > 2 ? tradeLine[2] : "missing", nfe);
                        continue;
                    }
                    bar.addTrade(tradeVolume, tradePrice);
                } else {
                    // the trade happened after the end of the bar
                    // go to the next bar but stay with the same trade (don't increment i)
                    // this break will drop us after the inner "while", skipping the increment
                    break;
                }
            } while (iterator.hasNext());
            // if the bar has any trades add it to the bars list
            // this is where the break drops to
            if (bar.getTrades() > 0) {
                series.addBar(bar);
            }
        } while (barEndTime.isBefore(endTime));
    }

    public static void main(String[] args) {
        BarSeries series = BitStampCsvTradesFileBarSeriesDataSource.loadBitstampSeries();

        LOG.debug("Series: {} ({})", series.getName(), series.getSeriesPeriodDescription());
        LOG.debug("Number of bars: {}", series.getBarCount());
        LOG.debug("First bar: \n\tVolume: {}\n\tNumber of trades: {}\n\tClose price: {}", series.getBar(0).getVolume(),
                series.getBar(0).getTrades(), series.getBar(0).getClosePrice());
    }
}
