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

import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.Num;

/**
 * This class builds a Ta4j bar series from a CSV file containing trades.
 */
public class CsvTradesLoader {

    /**
     * @return the bar series from Bitstamp (bitcoin exchange) trades
     */
    public static BarSeries loadBitstampSeries() {

        // Reading all lines of the CSV file
        InputStream stream = CsvTradesLoader.class.getClassLoader()
                .getResourceAsStream("bitstamp_trades_from_20131125_usd.csv");
        List<String[]> lines = null;
        assert stream != null;
        try (final var csvReader = new com.opencsv.CSVReader(new InputStreamReader(stream))) {
            lines = csvReader.readAll();
            lines.remove(0); // Removing header line
        } catch (Exception ioe) {
            Logger.getLogger(CsvTradesLoader.class.getName()).log(Level.SEVERE, "Unable to load trades from CSV", ioe);
        }

        var series = new BaseBarSeriesBuilder().build();
        if ((lines != null) && !lines.isEmpty()) {

            // Getting the first and last trades timestamps
            Instant beginTime = Instant.ofEpochMilli(Long.parseLong(lines.get(0)[0]) * 1000);
            Instant endTime = Instant.ofEpochMilli(Long.parseLong(lines.get(lines.size() - 1)[0]) * 1000);
            if (beginTime.isAfter(endTime)) {
                beginTime = endTime;
                // Since the CSV file has the most recent trades at the top of the file, we'll
                // reverse the list to feed
                // the List<Bar> correctly.
                Collections.reverse(lines);
            }
            // build the list of populated bars
            buildSeries(series, beginTime, endTime, 300, lines);
        }

        return series;
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
                Instant tradeTimeStamp = Instant.ofEpochMilli(Long.parseLong(tradeLine[0]) * 1000);
                // if the trade happened during the bar
                if (bar.inPeriod(tradeTimeStamp)) {
                    // add the trade to the bar
                    Num tradePrice = series.numFactory().numOf(Double.parseDouble(tradeLine[1]));
                    Num tradeVolume = series.numFactory().numOf(Double.parseDouble(tradeLine[2]));
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
        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
        System.out.println("Number of bars: " + series.getBarCount());
        System.out.println("First bar: \n" + "\tVolume: " + series.getBar(0).getVolume() + "\n" + "\tNumber of trades: "
                + series.getBar(0).getTrades() + "\n" + "\tClose price: " + series.getBar(0).getClosePrice());
    }
}
