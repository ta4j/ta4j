package net.sf.tail.indicator.tracker;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class EMAIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() {

		data = new SampleTimeSeries(new double[] { 64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95,
				63.37, 61.33, 61.51 });
	}

	@Test
	public void testEMAUsingTimeFrame10UsingClosePrice() {
		EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);

		assertEquals(63.65, ema.getValue(9), 0.01);
		assertEquals(63.23, ema.getValue(10), 0.01);
		assertEquals(62.91, ema.getValue(11), 0.01);
	}

	@Test
	public void testEMAFirstValueShouldBeEqualsToFirstDataValue() {
		EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);

		assertEquals(64.75, ema.getValue(0), 0.01);
	}

	@Test
	public void testValuesLessThanTimeFrameMustBeEqualsToSMAValues() {
		EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 10);

		for (int i = 0; i < 9; i++) {
			sma.getValue(i);
			ema.getValue(i);
			assertEquals(sma.getValue(i), ema.getValue(i));
		}
	}

	@Test
	public void testEMAShouldWorkJumpingIndexes() {
		EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);
		assertEquals(63.23, ema.getValue(10), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 10);
		assertEquals(3d, ema.getValue(14));
	}
	
	@Test
	public void testSmallTimeFrame()
	{
		EMAIndicator ema = new EMAIndicator(new ClosePriceIndicator(data), 1);
		assertEquals(64.75d, ema.getValue(0));
	}
	
}
