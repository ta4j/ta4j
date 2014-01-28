package eu.verdelhan.tailtest.loaders;

import au.com.bytecode.opencsv.CSVReader;
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

	List<DefaultTick> ticks;

	@Override
	public TimeSeries load(InputStream stream, String seriesName) {
		
		ticks = new ArrayList<DefaultTick>();
		CSVReader csvReader = null;
        try {
            csvReader = new CSVReader(new InputStreamReader(stream, Charset.forName("UTF-8")), ',');
            String[] line;
            while ((line = csvReader.readNext()) != null) {
//                Date timestamp = new Date(Long.parseLong(line[0]) * 1000);
//                BigMoney price = BigMoney.of(CurrencyUnit.USD, new BigDecimal(line[1]));
//                BigDecimal tradableAmount = new BigDecimal(line[2]);
//                Trade trade = new Trade(null, tradableAmount, Currencies.BTC, Currencies.USD, price, timestamp, 0);
//                trades.add(trade);
            }
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

		return new DefaultTimeSeries(seriesName, ticks);
	}

	/**
	 * @param tradeDate the datetime of the trade
	 * @param tradeAmount the tradable amount
	 * @param tradePrice the price
     */
    public void addTrade(DateTime tradeDate, BigDecimal tradeAmount, BigDecimal tradePrice) {
        if (ticks.isEmpty()) {
            // First trade
			DefaultTick tick = new DefaultTick(tradeDate, tradePrice);
			tick.addTrade(tradeAmount, tradePrice);
            ticks.add(tick);
        } else {
            // Subsequent trades
//            Tick lastTick = ticks.get(ticks.size() - 1);
//            while (!lastTick.inPeriod(trade.getTimestamp())) {
//                PREVIOUS_PERIODS.add(new Period(lastTick.getEndTimestamp()));
//                lastTick = PREVIOUS_PERIODS.get(PREVIOUS_PERIODS.size() - 1);
//            }
//            lastTick.addTrade(trade);
        }
    }

}
