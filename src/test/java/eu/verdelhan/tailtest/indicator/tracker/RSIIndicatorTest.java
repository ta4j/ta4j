package eu.verdelhan.tailtest.indicator.tracker;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class RSIIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() {
		data = new SampleTimeSeries(new double[] { 50.45, 50.30, 50.20, 50.15, 50.05, 50.06, 50.10, 50.08, 50.03,
				50.07, 50.01, 50.14, 50.22, 50.43, 50.50, 50.56, 50.52, 50.70, 50.55, 50.62, 50.90, 50.82, 50.86,
				51.20, 51.30, 51.10 });
	}

	@Test
	public void testRSIUsingTimeFrame14UsingClosePrice() {
		RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 14);

		assertEquals(62.75, rsi.getValue(15), 0.01);
		assertEquals(66.67, rsi.getValue(16), 0.01);
		assertEquals(75.23, rsi.getValue(17), 0.01);
		assertEquals(71.93, rsi.getValue(18), 0.01);
		assertEquals(73.33, rsi.getValue(19), 0.01);
		assertEquals(77.78, rsi.getValue(20), 0.01);
		assertEquals(74.67, rsi.getValue(21), 0.01);
		assertEquals(77.85, rsi.getValue(22), 0.01);
		assertEquals(81.56, rsi.getValue(23), 0.01);
		assertEquals(85.25, rsi.getValue(24), 0.01);
	}

	@Test
	public void testRSIFirstValueShouldBeZero() {
		RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 14);

		assertEquals(0d, rsi.getValue(0), 0.01);
	}

	@Test
	public void testRSIShouldWorkJumpingIndexes() {
		RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 14);
		assertEquals(73.33, rsi.getValue(19), 0.01);
		assertEquals(62.75, rsi.getValue(15), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		RSIIndicator rsi = new RSIIndicator(new ClosePriceIndicator(data), 14);
		assertEquals(3d, rsi.getValue(300));
	}
}
