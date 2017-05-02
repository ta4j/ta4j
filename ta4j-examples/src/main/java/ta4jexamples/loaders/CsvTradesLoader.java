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

import au.com.bytecode.opencsv.CSVReader;
import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;
import org.joda.time.Instant;
import org.joda.time.Period;

/**
 * This class build a Ta4j time series from a CSV file containing trades.
 */
public class CsvTradesLoader {

    /**
     * @return a time series from Bitstamp (bitcoin exchange) trades
     */
    public static TimeSeries loadBitstampSeries() {

        // Reading all lines of the CSV file
        InputStream stream = CsvTradesLoader.class.getClassLoader().getResourceAsStream("bitstamp_trades_from_20131125_usd.csv");
        CSVReader csvReader = null;
        List<String[]> lines = null;
        try {
            csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ',');
            lines = csvReader.readAll();
            lines.remove(0); // Removing header line
        } catch (IOException ioe) {
            Logger.getLogger(CsvTradesLoader.class.getName()).log(Level.SEVERE, "Unable to load trades from CSV", ioe);
        } finally {
            if (csvReader != null) {
                try {
                    csvReader.close();
                } catch (IOException ioe) {
                }
            }
        }

        List<Tick> ticks = null;
        if ((lines != null) && !lines.isEmpty()) {

            // Getting the first and last trades timestamps
            DateTime beginTime = new DateTime(Long.parseLong(lines.get(0)[0]) * 1000);
            DateTime endTime = new DateTime(Long.parseLong(lines.get(lines.size() - 1)[0]) * 1000);
            if (beginTime.isAfter(endTime)) {
                Instant beginInstant = beginTime.toInstant();
                Instant endInstant = endTime.toInstant();
                beginTime = new DateTime(endInstant);
                endTime = new DateTime(beginInstant);
                // Since the CSV file has the most recent trades at the top of the file, we'll reverse the list to feed the List<Tick> correctly.
                Collections.reverse(lines);
            }
            // Building the empty ticks (every 300 seconds, yeah welcome in Bitcoin world)
            ticks = buildEmptyTicks(beginTime, endTime, 300);
            // Filling the ticks with trades
            for (String[] tradeLine : lines) {
                DateTime tradeTimestamp = new DateTime(Long.parseLong(tradeLine[0]) * 1000);
                for (Tick tick : ticks) {
                    if (tick.inPeriod(tradeTimestamp)) {
                        double tradePrice = Double.parseDouble(tradeLine[1]);
                        double tradeAmount = Double.parseDouble(tradeLine[2]);
                        tick.addTrade(tradeAmount, tradePrice);
                    }
                }
            }
            // Removing still empty ticks
            removeEmptyTicks(ticks);
        }

        return new TimeSeries("bitstamp_trades", ticks);
    }

    /**
     * Builds a list of empty ticks.
     * @param beginTime the begin time of the whole period
     * @param endTime the end time of the whole period
     * @param duration the tick duration (in seconds)
     * @return the list of empty ticks
     */
    private static List<Tick> buildEmptyTicks(DateTime beginTime, DateTime endTime, int duration) {

        List<Tick> emptyTicks = new ArrayList<Tick>();

        Period tickTimePeriod = Period.seconds(duration);
        DateTime tickEndTime = beginTime;
        do {
            tickEndTime = tickEndTime.plus(tickTimePeriod);
            emptyTicks.add(new Tick(tickTimePeriod, tickEndTime));
        } while (tickEndTime.isBefore(endTime));

        return emptyTicks;
    }

    /**
     * Removes all empty (i.e. with no trade) ticks of the list.
     * @param ticks a list of ticks
     */
    private static void removeEmptyTicks(List<Tick> ticks) {
        for (int i = ticks.size() - 1; i >= 0; i--) {
            if (ticks.get(i).getTrades() == 0) {
                ticks.remove(i);
            }
        }
    }

    public static void main(String[] args) {
        TimeSeries series = CsvTradesLoader.loadBitstampSeries();

        System.out.println("Series: " + series.getName() + " (" + series.getSeriesPeriodDescription() + ")");
        System.out.println("Number of ticks: " + series.getTickCount());
        System.out.println("First tick: \n"
                + "\tVolume: " + series.getTick(0).getVolume() + "\n"
                + "\tNumber of trades: " + series.getTick(0).getTrades() + "\n"
                + "\tClose price: " + series.getTick(0).getClosePrice());
    }
}
