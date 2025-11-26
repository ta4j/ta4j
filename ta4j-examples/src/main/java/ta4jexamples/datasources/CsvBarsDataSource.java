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
package ta4jexamples.datasources;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * This class builds a Ta4j bar series from a CSV file containing bars.
 * <p>
 * Implements {@link BarSeriesDataSource} to support domain-driven loading by
 * ticker, interval, and date range. Searches for CSV files matching the
 * specified criteria in the classpath.
 */
public class CsvBarsDataSource implements BarSeriesDataSource {

    private static final Logger LOG = LogManager.getLogger(CsvBarsDataSource.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter FILENAME_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final String DEFAULT_APPLE_BAR_FILE = "AAPL-PT1D-20130102_20131231.csv";

    @Override
    public BarSeries loadSeries(String ticker, Duration interval, Instant start, Instant end) {
        if (ticker == null || ticker.trim().isEmpty()) {
            throw new IllegalArgumentException("Ticker cannot be null or empty");
        }
        if (interval == null || interval.isNegative() || interval.isZero()) {
            throw new IllegalArgumentException("Interval must be positive");
        }
        if (start == null || end == null) {
            throw new IllegalArgumentException("Start and end dates cannot be null");
        }
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("Start date must be before or equal to end date");
        }

        // Build search patterns for filename matching
        // Standard pattern: {ticker}-{interval}-{startDate}_{endDate}.csv
        String startDateStr = start.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
        String endDateStr = end.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
        String intervalStr = formatIntervalForFilename(interval);

        // Try exact pattern first: {ticker}-{interval}-{startDate}_{endDate}.csv
        String exactPattern = ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_" + endDateStr + ".csv";
        BarSeries series = loadCsvSeries(exactPattern);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Try broader pattern: {ticker}-*-{startDate}_*.csv
        String broaderPattern = ticker.toUpperCase() + "-*-" + startDateStr + "_*.csv";
        series = searchAndLoadCsvFile(broaderPattern, start, end);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Try even broader: {ticker}-*.csv (then filter by date range)
        String broadestPattern = ticker.toUpperCase() + "-*.csv";
        series = searchAndLoadCsvFile(broadestPattern, start, end);
        if (series != null && !series.isEmpty()) {
            return filterSeriesByDateRange(series, start, end);
        }

        LOG.debug("No CSV file found matching ticker: {}, interval: {}, date range: {} to {}", ticker, interval, start,
                end);
        return null;
    }

    @Override
    public BarSeries loadSeries(String source) {
        if (source == null || source.trim().isEmpty()) {
            throw new IllegalArgumentException("Source cannot be null or empty");
        }
        return loadCsvSeries(source);
    }

    /**
     * Formats a Duration as an ISO 8601 interval string for use in filenames.
     *
     * @param interval the duration to format
     * @return the ISO 8601 duration string (e.g., "PT1D", "PT5M", "PT1H")
     */
    private String formatIntervalForFilename(Duration interval) {
        long seconds = interval.getSeconds();
        if (seconds % 86400 == 0) {
            return "PT" + (seconds / 86400) + "D";
        } else if (seconds % 3600 == 0) {
            return "PT" + (seconds / 3600) + "H";
        } else if (seconds % 60 == 0) {
            return "PT" + (seconds / 60) + "M";
        } else {
            return "PT" + seconds + "S";
        }
    }

    /**
     * Searches for a CSV file matching the pattern and loads it if found.
     *
     * @param pattern the filename pattern to search for (supports wildcards)
     * @param start   the start date (for validation)
     * @param end     the end date (for validation)
     * @return the loaded BarSeries, or null if not found
     */
    private BarSeries searchAndLoadCsvFile(String pattern, Instant start, Instant end) {
        // Try direct pattern match as resource
        if (pattern.contains("*")) {
            // For wildcard patterns, try common variations
            String[] variations = { pattern.replace("*", "PT1D"), pattern.replace("*", "PT5M"),
                    pattern.replace("*", "") };
            for (String variation : variations) {
                BarSeries series = loadCsvSeries(variation);
                if (series != null && !series.isEmpty()) {
                    return series;
                }
            }
        } else {
            // Direct match
            return loadCsvSeries(pattern);
        }
        return null;
    }

    /**
     * Filters a BarSeries to only include bars within the specified date range.
     *
     * @param series the series to filter
     * @param start  the start date (inclusive)
     * @param end    the end date (inclusive)
     * @return a new BarSeries containing only bars within the date range, or null
     *         if no bars match
     */
    private BarSeries filterSeriesByDateRange(BarSeries series, Instant start, Instant end) {
        if (series == null || series.isEmpty()) {
            return null;
        }

        var filteredSeries = new BaseBarSeriesBuilder().withName(series.getName()).build();
        for (int i = 0; i < series.getBarCount(); i++) {
            var bar = series.getBar(i);
            Instant barEnd = bar.getEndTime();
            if (!barEnd.isBefore(start) && !barEnd.isAfter(end)) {
                filteredSeries.addBar(bar);
            }
        }

        return filteredSeries.isEmpty() ? null : filteredSeries;
    }

    /**
     * Loads a bar series from the default CSV file.
     *
     * @return the bar series loaded from the default CSV file
     */
    public static BarSeries loadSeriesFromFile() {
        return loadSeriesFromFile(DEFAULT_APPLE_BAR_FILE);
    }

    /**
     * Loads a bar series from the specified CSV file. This is a convenience method
     * that delegates to {@link #loadCsvSeries(String)}.
     *
     * @param csvFile the path to the CSV file containing bar data
     * @return the bar series loaded from the specified CSV file
     */
    public static BarSeries loadSeriesFromFile(String csvFile) {
        return loadCsvSeries(csvFile);
    }

    /**
     * Loads a bar series from a CSV file with the specified filename. The CSV file
     * is expected to contain stock market data with the following columns: date,
     * open price, high price, low price, close price, and volume. The date format
     * is expected to match the predefined DATE_FORMAT.
     *
     * @param filename the name of the CSV file to load
     * @return the bar series containing stock data loaded from the specified CSV
     *         file, or null if the file is not found or empty
     */
    public static BarSeries loadCsvSeries(String filename) {

        var stream = CsvBarsDataSource.class.getClassLoader().getResourceAsStream(filename);

        if (stream == null) {
            LOG.debug("CSV file not found in classpath: {}", filename);
            return null;
        }

        var series = new BaseBarSeriesBuilder().withName(filename).build();

        try {
            try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                    .withSkipLines(1)
                    .build()) {
                String[] line;
                while ((line = csvReader.readNext()) != null) {
                    Instant date = LocalDate.parse(line[0], DATE_FORMAT).atStartOfDay(ZoneOffset.UTC).toInstant();
                    double open = Double.parseDouble(line[1]);
                    double high = Double.parseDouble(line[2]);
                    double low = Double.parseDouble(line[3]);
                    double close = Double.parseDouble(line[4]);
                    double volume = Double.parseDouble(line[5]);

                    series.barBuilder()
                            .timePeriod(Duration.ofDays(1))
                            .endTime(date)
                            .openPrice(open)
                            .closePrice(close)
                            .highPrice(high)
                            .lowPrice(low)
                            .volume(volume)
                            .amount(0)
                            .add();
                }
            } catch (CsvValidationException e) {
                LOG.error("Unable to load bars from CSV. File is not valid csv.", e);
            }
        } catch (IOException ioe) {
            LOG.error("Unable to load bars from CSV", ioe);
        } catch (NumberFormatException nfe) {
            LOG.error("Error while parsing value", nfe);
            return null;
        }
        return series.isEmpty() ? null : series;
    }

    public static void main(String[] args) {
        BarSeries series = CsvBarsDataSource.loadSeriesFromFile();

        LOG.debug("Series: {} ({})", series.getName(), series.getSeriesPeriodDescription());
        LOG.debug("Number of bars: {}", series.getBarCount());
        if (series.isEmpty()) {
            LOG.warn("Series is empty - no bars loaded from CSV file. Skipping first bar details.");
        } else {
            LOG.debug("First bar: \n\tVolume: {}\n\tOpen price: {}\n\tClose price: {}", series.getBar(0).getVolume(),
                    series.getBar(0).getOpenPrice(), series.getBar(0).getClosePrice());
        }
    }
}
