/*
 * SPDX-License-Identifier: MIT
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
import ta4jexamples.datasources.file.AbstractFileBarSeriesDataSource;

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
public class CsvFileBarSeriesDataSource extends AbstractFileBarSeriesDataSource {

    private static final Logger LOG = LogManager.getLogger(CsvFileBarSeriesDataSource.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_APPLE_BAR_FILE = "AAPL-PT1D-20130102_20131231.csv";

    /**
     * Creates a new CsvFileBarSeriesDataSource with no source prefix.
     */
    public CsvFileBarSeriesDataSource() {
        super("");
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
        // {ticker}-{interval}-{startDateTime}_{endDateTime}.csv
        String exactPattern = ticker.toUpperCase() + "-" + intervalStr + "-" + startDateTimeStr + "_" + endDateTimeStr
                + ".csv";
        BarSeries series = loadCsvSeries(exactPattern);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Fallback to date-only format for existing files
        String exactPatternDateOnly = ticker.toUpperCase() + "-" + intervalStr + "-" + startDateStr + "_" + endDateStr
                + ".csv";
        series = loadCsvSeries(exactPatternDateOnly);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Try broader pattern: {ticker}-*-{startDateTime}_*.csv
        String broaderPattern = ticker.toUpperCase() + "-*-" + startDateTimeStr + "_*.csv";
        series = searchAndLoadCsvFile(broaderPattern, start, end);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Fallback to date-only format for broader pattern
        String broaderPatternDateOnly = ticker.toUpperCase() + "-*-" + startDateStr + "_*.csv";
        series = searchAndLoadCsvFile(broaderPatternDateOnly, start, end);
        if (series != null && !series.isEmpty()) {
            return series;
        }

        // Try even broader: {ticker}-*.csv (then filter by date range)
        String broadestPattern = ticker.toUpperCase() + "-*.csv";
        series = searchAndLoadCsvFile(broadestPattern, start, end);
        if (series != null && !series.isEmpty()) {
            return filterSeriesByDateRange(series, start, end);
        }

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
     * Searches for a CSV file matching the pattern and loads it if found.
     *
     * @param pattern the filename pattern to search for (supports wildcards)
     * @param start   the start date (for validation)
     * @param end     the end date (for validation)
     * @return the loaded BarSeries, or null if not found
     */
    private BarSeries searchAndLoadCsvFile(String pattern, Instant start, Instant end) {
        // Try direct pattern match as resource
        if (!pattern.contains("*")) {
            return loadCsvSeries(pattern);
        }

        // Count wildcards to determine replacement strategy
        long wildcardCount = pattern.chars().filter(ch -> ch == '*').count();

        if (wildcardCount == 1) {
            // Single wildcard: try common interval values
            String[] intervalVariations = { "PT1D", "PT5M", "PT1H", "" };
            for (String interval : intervalVariations) {
                String variation = pattern.replaceFirst("\\*", interval);
                BarSeries series = loadCsvSeries(variation);
                if (series != null && !series.isEmpty()) {
                    return series;
                }
            }
        } else if (wildcardCount >= 2) {
            // Multiple wildcards: replace the first (interval) with common values,
            // and the second (end date) with the actual end date from parameters
            String[] intervalVariations = { "PT1D", "PT5M", "PT1H" };
            // Format end date in both date-only and datetime formats
            String endDateStr = end.atZone(ZoneOffset.UTC).format(FILENAME_DATE_FORMAT);
            String endDateTimeStr = end.atZone(ZoneOffset.UTC)
                    .format(getDateTimeFormatterForInterval(Duration.ofDays(1)));

            for (String interval : intervalVariations) {
                // Replace first wildcard with interval, second with end date (date-only format)
                String variation = pattern.replaceFirst("\\*", interval).replaceFirst("\\*", endDateStr);
                BarSeries series = loadCsvSeries(variation);
                if (series != null && !series.isEmpty()) {
                    return series;
                }
                // Also try with datetime format for the end date
                variation = pattern.replaceFirst("\\*", interval).replaceFirst("\\*", endDateTimeStr);
                series = loadCsvSeries(variation);
                if (series != null && !series.isEmpty()) {
                    return series;
                }
            }
        }

        return null;
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

        var stream = CsvFileBarSeriesDataSource.class.getClassLoader().getResourceAsStream(filename);

        if (stream == null) {
            LOG.debug("CSV file not found in classpath: {}", filename);
            return null;
        }

        var series = new BaseBarSeriesBuilder().withName(filename).build();

        try (stream) {
            try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                try (CSVReader csvReader = new CSVReaderBuilder(reader)
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
        BarSeries series = CsvFileBarSeriesDataSource.loadSeriesFromFile();

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
