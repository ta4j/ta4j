package eu.verdelhan.tailtest.io.reader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.TimeSeriesLoader;
import eu.verdelhan.tailtest.series.DefaultTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.apache.log4j.Logger;
import org.joda.time.DateTime;

import com.csvreader.CsvReader;

public class GoogleTimeSeriesLoader implements TimeSeriesLoader {
	
	transient private static final Logger LOG = Logger.getLogger(GoogleTimeSeriesLoader.class);

	public TimeSeries load(InputStream stream, String seriesName) {

		List<DefaultTick> ticks= new ArrayList<DefaultTick>();
		try {
			LOG.info("Reading " + stream);
			CsvReader reader = new CsvReader(stream, Charset.defaultCharset());
			
			reader.readHeaders();

			while (reader.readRecord()) {
				SimpleDateFormat simpleDate;

				simpleDate = new SimpleDateFormat("dd-MMM-yy");

				Date dateUtil = simpleDate.parse(reader.get(0));

				DateTime date = new DateTime(dateUtil.getTime());

				 double open = Double.parseDouble(reader.get(1));
				 double high = Double.parseDouble(reader.get(2));
				 double low = Double.parseDouble(reader.get(3));
				 double close = Double.parseDouble(reader.get(4));
				 double volumeFinancier = Double.parseDouble(reader.get(5));

				 DefaultTick tick = new DefaultTick(date, open, close, high, low, 0d, 0d, 0d, volumeFinancier, 0);
				ticks.add(0, tick);
			}

			LOG.info("Reading done for " + stream);
		} catch (NumberFormatException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		} catch (ParseException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		return new DefaultTimeSeries(seriesName, ticks);
	}
}
