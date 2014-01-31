package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class EMATest {

	private TimeSeries data;

	@Before
	public void setUp() {

		data = new MockTimeSeries(new double[] { 64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95,
				63.37, 61.33, 61.51 });
	}

	@Test
	public void testEMAUsingTimeFrame10UsingClosePrice() {
		EMA ema = new EMA(new ClosePrice(data), 10);

		assertEquals(63.65, ema.getValue(9), 0.01);
		assertEquals(63.23, ema.getValue(10), 0.01);
		assertEquals(62.91, ema.getValue(11), 0.01);
	}

	@Test
	public void testEMAFirstValueShouldBeEqualsToFirstDataValue() {
		EMA ema = new EMA(new ClosePrice(data), 10);

		assertEquals(64.75, ema.getValue(0), 0.01);
	}

	@Test
	public void testValuesLessThanTimeFrameMustBeEqualsToSMAValues() {
		EMA ema = new EMA(new ClosePrice(data), 10);
		SMA sma = new SMA(new ClosePrice(data), 10);

		for (int i = 0; i < 9; i++) {
			sma.getValue(i);
			ema.getValue(i);
			assertEquals(sma.getValue(i), ema.getValue(i));
		}
	}

	@Test
	public void testEMAShouldWorkJumpingIndexes() {
		EMA ema = new EMA(new ClosePrice(data), 10);
		assertEquals(63.23, ema.getValue(10), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		EMA ema = new EMA(new ClosePrice(data), 10);
		assertEquals(3d, (double) ema.getValue(14));
	}
	
	@Test
	public void testSmallTimeFrame()
	{
		EMA ema = new EMA(new ClosePrice(data), 1);
		assertEquals(64.75d, (double) ema.getValue(0));
	}
	
}
