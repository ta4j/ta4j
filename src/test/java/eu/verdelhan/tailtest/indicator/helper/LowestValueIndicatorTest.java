package eu.verdelhan.tailtest.indicator.helper;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class LowestValueIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1 });
	}

	@Test
	public void testLowestValueIndicatorUsingTimeFrame5UsingClosePrice() throws Exception {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);

		assertEquals(1d, lowestValue.getValue(4), 0.01);
		assertEquals(2d, lowestValue.getValue(5), 0.01);
		assertEquals(3d, lowestValue.getValue(6), 0.01);
		assertEquals(3d, lowestValue.getValue(7), 0.01);
		assertEquals(3d, lowestValue.getValue(8), 0.01);
		assertEquals(3d, lowestValue.getValue(9), 0.01);
		assertEquals(2d, lowestValue.getValue(10), 0.01);
		assertEquals(2d, lowestValue.getValue(11), 0.01);
		assertEquals(2d, lowestValue.getValue(12), 0.01);

	}

	@Test
	public void testLowestValueShouldWorkJumpingIndexes() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(2d, lowestValue.getValue(10), 0.01);
		assertEquals(3d, lowestValue.getValue(6), 0.01);
	}

	@Test
	public void testLowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(1d, lowestValue.getValue(0), 0.01);
	}

	@Test
	public void testLowestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 500);
		assertEquals(1d, lowestValue.getValue(12), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(3d, lowestValue.getValue(300));
	}

	@Test
	public void testGetName() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
		assertEquals("LowestValueIndicator timeFrame: 5", lowestValue.getName());
	}
}
