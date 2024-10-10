/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
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
package ta4jexamples.loaders;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

/**
 * This class build a Ta4j bar series from a CSV file containing bars.
 */
public class CsvBarsLoader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @return the bar series from Apple Inc. bars.
     */

    public static BarSeries loadAppleIncSeries() {
        return loadCsvSeries("appleinc_bars_from_20130101_usd.csv");
    }

    public static BarSeries loadCsvSeries(String filename) {

        var stream = CsvBarsLoader.class.getClassLoader().getResourceAsStream(filename);

        var series = new BaseBarSeriesBuilder().withName("apple_bars").build();

        try {
            assert stream != null;
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
                Logger.getLogger(CsvBarsLoader.class.getName())
                        .log(Level.SEVERE, "Unable to load bars from CSV. File is not valid csv.", e);
            }
        } catch (IOException ioe) {
            Logger.getLogger(CsvBarsLoader.class.getName()).log(Level.SEVERE, "Unable to load bars from CSV", ioe);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(CsvBarsLoader.class.getName()).log(Level.SEVERE, "Error while parsing value", nfe);
        }
        return series;
    }

    public static void main(String[] args) {
        BarSeries series = CsvBarsLoader.loadAppleIncSeries();

        System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
        System.out.println("Number of bars: " + series.getBarCount());
        System.out.println("First bar: \n" + "\tVolume: " + series.getBar(0).getVolume() + "\n" + "\tOpen price: "
                + series.getBar(0).getOpenPrice() + "\n" + "\tClose price: " + series.getBar(0).getClosePrice());
    }
}
