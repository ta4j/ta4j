package eu.verdelhan.tailtest.loaders;

import au.com.bytecode.opencsv.CSVReader;
import eu.verdelhan.tailtest.Tick;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.TimeSeriesLoader;
import eu.verdelhan.tailtest.series.DefaultTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.joda.time.DateTime;

/**
 * CSV trades loader
 */
public class CsvTradesLoader implements TimeSeriesLoader {

	List<Tick> ticks;

	@Override
	public TimeSeries load(InputStream stream, String seriesName) {
		
		CSVReader csvReader = null;
		List<String[]> lines = null;
        try {
            csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ',');
			lines = csvReader.readAll();
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

		if ((lines != null) && !lines.isEmpty()) {

			// Getting the first and last trades timestamps
			DateTime beginTime = new DateTime(Long.parseLong(lines.get(0)[0]) * 1000);
			DateTime endTime = new DateTime(Long.parseLong(lines.get(lines.size() - 1)[0]) * 1000);
			// Building the empty ticks
			ticks = buildEmptyTicks(beginTime, endTime, 600);
			// Filling the ticks with trades
			for (String[] tradeLine : lines) {
				DateTime tradeTimestamp = new DateTime(Long.parseLong(tradeLine[0]) * 1000);
				for (Tick tick : ticks) {
					if (tick.inPeriod(tradeTimestamp)) {
						BigDecimal tradePrice = new BigDecimal(tradeLine[1]);
						BigDecimal tradeAmount = new BigDecimal(tradeLine[2]);
						tick.addTrade(tradeAmount, tradePrice);
					}
				}
			}
			// Removing still empty ticks
			removeEmptyTicks(ticks);
		}

		return new DefaultTimeSeries(seriesName, ticks);
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

		DateTime tickBeginTime = beginTime;
		DateTime tickEndTime;
		do {
			tickEndTime = tickBeginTime.plusSeconds(duration);
			emptyTicks.add(new DefaultTick(tickBeginTime, tickEndTime));
			tickBeginTime = tickEndTime;
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
}
