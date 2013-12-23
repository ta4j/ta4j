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

public class CedroTimeSeriesLoader implements TimeSeriesLoader {

	transient private static final Logger LOG = Logger.getLogger(CedroTimeSeriesLoader.class);

	public TimeSeries load(InputStream stream, String seriesName) {

		List<DefaultTick> ticks= new ArrayList<DefaultTick>();
		try {
			LOG.info("Reading " + seriesName);
			CsvReader reader = new CsvReader(stream, Charset.defaultCharset());
			
			reader.readHeaders();

			while (reader.readRecord()) {
				SimpleDateFormat simpleDate;

				if (reader.get(0).length() > 10)
					simpleDate = new SimpleDateFormat("dd/M/yyyy HH:mm:ss");
				else
					simpleDate = new SimpleDateFormat("dd/M/yyyy");

				Date dateUtil = simpleDate.parse(reader.get(0));

				DateTime date = new DateTime(dateUtil.getTime());

				double open = java.lang.Double.parseDouble(reader.get(1));
				double close = java.lang.Double.parseDouble(reader.get(2));
				double high = java.lang.Double.parseDouble(reader.get(3));
				double low = java.lang.Double.parseDouble(reader.get(4));
				//double change = java.lang.Double.parseDouble(reader.get(5));
				//double previous = java.lang.Double.parseDouble(reader.get(6));
				//double volumeAmount = java.lang.Double.parseDouble(reader.get(7));
				//double volumeFinancier = java.lang.Double.parseDouble(reader.get(8));
				//double quantity = java.lang.Double.parseDouble(reader.get(9));

				DefaultTick tick = new DefaultTick(date, open, close, high, low);
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
