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
 * This class build a Ta4j bar series from a CSV file containing bars.
 */
public class CsvBarsDataSource {

    private static final Logger LOG = LogManager.getLogger(CsvBarsDataSource.class);
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final String DEFAULT_APPLE_BAR_FILE = "appleinc_bars_from_20130101_usd.csv";

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
     *         file
     */
    public static BarSeries loadCsvSeries(String filename) {

        var stream = CsvBarsDataSource.class.getClassLoader().getResourceAsStream(filename);

        if (stream == null) {
            LOG.error("Unable to load CSV file: {} not found in classpath", filename);
            return new BaseBarSeriesBuilder().withName(filename).build();
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
        }
        return series;
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
