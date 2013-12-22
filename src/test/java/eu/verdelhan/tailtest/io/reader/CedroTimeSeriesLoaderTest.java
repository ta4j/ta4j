package net.sf.tail.io.reader;

import static org.junit.Assert.assertEquals;

import java.io.FileInputStream;

import net.sf.tail.TimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

public class CedroTimeSeriesLoaderTest {
	private CedroTimeSeriesLoader ctsl;

	private TimeSeries ts;

	private DateTime date;

	@Before
	public void setUp() throws Exception {
		date = new DateTime();
		ctsl = new CedroTimeSeriesLoader();
		// TODO: isso deveria ser um InputStream harcoded, e nao um
		// vindo de um FileInputStream
		// alem disso, o metodo load deveria ficar em um test
		ts = ctsl.load(new FileInputStream("BaseBovespa/tests/Cedro-ReaderTest.csv"), "");
	}

	@Test
	public void testLine3() throws Exception {
		date = date.withDate(2007, 5, 2).withTime(0, 0, 0, 0);
		DefaultTick tick = new DefaultTick(date, 71.70, 72.06, 72.75, 71.70, 0.99, 72.81, 108200.00, 7854215.00, 152);
		assertEquals(tick, ts.getTick(2));
	}

	@Test
	public void testLine2() throws Exception {
		date = date.withDate(2007, 4, 30).withTime(0, 0, 0, 0);
		DefaultTick tick = new DefaultTick(date, 73.09, 72.81, 73.10, 72.20, 1.00, 73.09, 83200.00, 6045660.00, 103);
		assertEquals(tick, ts.getTick(1));
	}

	@Test
	public void testLine1() throws Exception {
		date = date.withDate(2007, 4, 27).withTime(0, 0, 0, 0);
		DefaultTick tick = new DefaultTick(date, 71.00, 73.09, 73.29, 68.76, 1.02, 71.40, 59100.00, 4180018.00, 141);
		assertEquals(tick, ts.getTick(0));

	}
}
