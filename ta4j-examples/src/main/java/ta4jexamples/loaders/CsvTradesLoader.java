/*
  The MIT License (MIT)

  Copyright (c) 2014-2017 Marc de Verdelhan & respective authors (see AUTHORS)

  Permission is hereby granted, free of charge, to any person obtaining a copy of
  this software and associated documentation files (the "Software"), to deal in
  the Software without restriction, including without limitation the rights to
  use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
  the Software, and to permit persons to whom the Software is furnished to do so,
  subject to the following conditions:

  The above copyright notice and this permission notice shall be included in all
  copies or substantial portions of the Software.

  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
  FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
  COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
  IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
  CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package ta4jexamples.loaders;

import com.opencsv.CSVReader;
import org.ta4j.core.BaseTick;
import org.ta4j.core.BaseTimeSeries;
import org.ta4j.core.Tick;
import org.ta4j.core.TimeSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * This class builds a Ta4j time series from a CSV file containing trades.
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
            ZonedDateTime beginTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(lines.get(0)[0]) * 1000), ZoneId.systemDefault());
            ZonedDateTime endTime = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(lines.get(lines.size() - 1)[0]) * 1000), ZoneId.systemDefault());
            if (beginTime.isAfter(endTime)) {
                Instant beginInstant = beginTime.toInstant();
                Instant endInstant = endTime.toInstant();
                beginTime = ZonedDateTime.ofInstant(endInstant, ZoneId.systemDefault());
                endTime = ZonedDateTime.ofInstant(beginInstant, ZoneId.systemDefault());
                // Since the CSV file has the most recent trades at the top of the file, we'll reverse the list to feed the List<Tick> correctly.
                Collections.reverse(lines);
            }
            // build the list of populated ticks
           	ticks = buildTicks(beginTime, endTime, 300, lines);
        }

        return new BaseTimeSeries("bitstamp_trades", ticks);
    }
    
    /**
     * Builds a list of populated ticks from csv data.
     * @param beginTime the begin time of the whole period
     * @param endTime the end time of the whole period
     * @param duration the tick duration (in seconds)
     * @param lines the csv data returned by CSVReader.readAll()
     * @return the list of populated ticks
     */
    private static List<Tick> buildTicks(ZonedDateTime beginTime, ZonedDateTime endTime, int duration, List<String[]> lines) {
    	
    	List<Tick> ticks = new ArrayList<>();
    	
    	Duration tickDuration = Duration.ofSeconds(duration);
    	ZonedDateTime tickEndTime = beginTime;
    	// line number of trade data
    	int i = 0;
    	do {
    		// build a tick
    		tickEndTime = tickEndTime.plus(tickDuration);
    		Tick tick = new BaseTick(tickDuration, tickEndTime);
    		do {
    			// get a trade
    			String[] tradeLine = lines.get(i);
    			ZonedDateTime tradeTimeStamp = ZonedDateTime.ofInstant(Instant.ofEpochMilli(Long.parseLong(tradeLine[0]) * 1000), ZoneId.systemDefault());
    			// if the trade happened during the tick
    			if (tick.inPeriod(tradeTimeStamp)) {
    				// add the trade to the tick
    				double tradePrice = Double.parseDouble(tradeLine[1]);
    				double tradeAmount = Double.parseDouble(tradeLine[2]);
    				tick.addTrade(tradeAmount, tradePrice);
    			} else {
    				// the trade happened after the end of the tick
    				// go to the next tick but stay with the same trade (don't increment i)
    				// this break will drop us after the inner "while", skipping the increment
    				break;
    			}
    			i++;
    		} while (i < lines.size());
    		// if the tick has any trades add it to the ticks list 
    		// this is where the break drops to
    		if (tick.getTrades() > 0) {
    			ticks.add(tick);
    		}
    	} while (tickEndTime.isBefore(endTime));
    	return ticks;
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
