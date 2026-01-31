/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.mocks.MockIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import com.opencsv.CSVParserBuilder;
import com.opencsv.CSVReader;
import com.opencsv.CSVReaderBuilder;
import com.opencsv.exceptions.CsvValidationException;

public class CsvTestUtils {

    private static Logger log = LoggerFactory.getLogger(CsvTestUtils.class.getName());

    public static MockIndicator getCsvFile(Class<?> clazz, String fileName, NumFactory numFactory) {
        InputStream inputStream = clazz.getResourceAsStream(fileName);
        if (inputStream == null) {
            throw new RuntimeException("Null InputStream for file " + fileName);
        }
        try (CSVReader csvReader = new CSVReaderBuilder(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .withCSVParser(new CSVParserBuilder().withSeparator(',').build())
                .withSkipLines(1)
                .build()) {
            String[] line;

            BarSeries series = new MockBarSeriesBuilder().withName("CVS series").withNumFactory(numFactory).build();
            List<Num> values = new ArrayList<>();

            while ((line = csvReader.readNext()) != null) {

                LocalDateTime dateTime = parseDate(line[0]);
                double open = Double.parseDouble(line[1]);
                double high = Double.parseDouble(line[2]);
                double low = Double.parseDouble(line[3]);
                double close = Double.parseDouble(line[4]);
                double volume = Double.parseDouble(line[5]);
                double ma = Double.parseDouble(line[6]);

                Instant instant = dateTime.toInstant(ZoneOffset.UTC);
                Bar bar = series.barBuilder()
                        .timePeriod(Duration.ofMinutes(1))
                        .endTime(instant)
                        .openPrice(numFactory.numOf(open))
                        .highPrice(numFactory.numOf(high))
                        .lowPrice(numFactory.numOf(low))
                        .closePrice(numFactory.numOf(close))
                        .volume(numFactory.numOf(volume))
                        .build();

                series.addBar(bar);
                values.add(numFactory.numOf(ma));
            }

            return new MockIndicator(series, values);
        } catch (CsvValidationException | IOException e) {
            log.error("Error while reading CSV file", e);
        }
        return null;
    }

    public static LocalDateTime parseDate(String dateString) {
        List<DateTimeFormatter> formatters = Arrays.asList(DateTimeFormatter.ISO_OFFSET_DATE_TIME,
                DateTimeFormatter.ISO_LOCAL_DATE_TIME, DateTimeFormatter.ISO_LOCAL_DATE,
                DateTimeFormatter.ofPattern("MM/d/yyyy HH:mm:ss"), DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"),
                DateTimeFormatter.ofPattern("yyyy-MM-dd"), DateTimeFormatter.ofPattern("MM/dd/yy"),
                DateTimeFormatter.ofPattern("dd/MM/yyyy"));

        for (DateTimeFormatter formatter : formatters) {
            try {
                return LocalDateTime.parse(dateString, formatter);
            } catch (DateTimeParseException e) {
            }
        }

        throw new IllegalArgumentException("Could not parse date: " + dateString);
    }

}
