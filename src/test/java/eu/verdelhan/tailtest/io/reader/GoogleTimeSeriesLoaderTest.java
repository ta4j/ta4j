package eu.verdelhan.tailtest.io.reader;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

public class GoogleTimeSeriesLoaderTest {
	private GoogleTimeSeriesLoader ctsl;

	private TimeSeries ts;

	private DateTime date;

	@Before
	public void setUp() throws Exception {
		date = new DateTime();
		ctsl = new GoogleTimeSeriesLoader();
		// TODO: isso deveria ser um InputStream harcoded, e nao um
		// vindo de um FileInputStream
		// alem disso, o metodo load deveria ficar em um test
		ts = ctsl.load(new FileInputStream("BaseBovespa/tests/MMM.csv"), "");
	}

	@Test
	public void testLine3() throws Exception {
		date = date.withDate(2000, 1, 26).withTime(0, 0, 0, 0);
		DefaultTick tick = new DefaultTick(date, 47.94, 48.16, 48.75,  47.66, 0d, 0d, 0d, 7020800, 0);
		assertEquals(tick, ts.getTick(2));
	}

	@Test
	public void testLine2() throws Exception {
		date = date.withDate(2000, 1, 25).withTime(0, 0, 0, 0);
		DefaultTick tick = new DefaultTick(date, 47.44, 45.69, 47.44, 45.12, 0d, 0d, 0d, 3691200, 0);
		assertEquals(tick, ts.getTick(1));
	}
}
