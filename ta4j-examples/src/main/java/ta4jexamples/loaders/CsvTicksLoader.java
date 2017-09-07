/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)
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

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.BaseTick;
import eu.verdelhan.ta4j.BaseTimeSeries;
import eu.verdelhan.ta4j.Tick;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.opencsv.CSVReader;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * This class build a Ta4j time series from a CSV file containing ticks.
 */
public class CsvTicksLoader {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * @return a time series from Apple Inc. ticks.
     */
    public static TimeSeries loadAppleIncSeries() {

        InputStream stream = CsvTicksLoader.class.getClassLoader().getResourceAsStream("appleinc_ticks_from_20130101_usd.csv");

        List<Tick> ticks = new ArrayList<>();

        CSVReader csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ',', '"', 1);
        try {
            String[] line;
            while ((line = csvReader.readNext()) != null) {
                ZonedDateTime date = LocalDate.parse(line[0], DATE_FORMAT).atStartOfDay(ZoneId.systemDefault());
                double open = Double.parseDouble(line[1]);
                double high = Double.parseDouble(line[2]);
                double low = Double.parseDouble(line[3]);
                double close = Double.parseDouble(line[4]);
                double volume = Double.parseDouble(line[5]);

                ticks.add(new BaseTick(date, open, high, low, close, volume));
            }
        } catch (IOException ioe) {
            Logger.getLogger(CsvTicksLoader.class.getName()).log(Level.SEVERE, "Unable to load ticks from CSV", ioe);
        } catch (NumberFormatException nfe) {
            Logger.getLogger(CsvTicksLoader.class.getName()).log(Level.SEVERE, "Error while parsing value", nfe);
        }

        return new BaseTimeSeries("apple_ticks", ticks);
    }

    public static void main(String[] args) {
        TimeSeries series = CsvTicksLoader.loadAppleIncSeries();

        System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
        System.out.println("Number of ticks: " + series.getTickCount());
        System.out.println("First tick: \n"
                + "\tVolume: " + series.getTick(0).getVolume() + "\n"
                + "\tOpen price: " + series.getTick(0).getOpenPrice()+ "\n"
                + "\tClose price: " + series.getTick(0).getClosePrice());
    }
}
