package eu.verdelhan.tailtest.indicator.helper;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class HighestValueIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testHighestValueUsingTimeFrame5UsingClosePrice() throws Exception {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

		assertEquals(4d, highestValue.getValue(4), 0.01);
		assertEquals(4d, highestValue.getValue(5), 0.01);
		assertEquals(5d, highestValue.getValue(6), 0.01);
		assertEquals(6d, highestValue.getValue(7), 0.01);
		assertEquals(6d, highestValue.getValue(8), 0.01);
		assertEquals(6d, highestValue.getValue(9), 0.01);
		assertEquals(6d, highestValue.getValue(10), 0.01);
		assertEquals(6d, highestValue.getValue(11), 0.01);
		assertEquals(4d, highestValue.getValue(12), 0.01);

	}

	@Test
	public void testFirstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(1d, highestValue.getValue(0), 0.01);
	}

	@Test
	public void testHighestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
		assertEquals(6d, highestValue.getValue(12), 0.01);
	}

	@Test
	public void testHighestValueShouldWorkJumpingIndexes() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(5d, highestValue.getValue(6), 0.01);
		assertEquals(4, highestValue.getValue(12), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(3d, highestValue.getValue(300));
	}

	@Test
	public void testGetName() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals("HighestValueIndicator timeFrame: 5", highestValue.getName());
	}
}
