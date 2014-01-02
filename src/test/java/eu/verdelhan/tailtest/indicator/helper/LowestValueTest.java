package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class LowestValueTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1 });
	}

	@Test
	public void testLowestValueIndicatorUsingTimeFrame5UsingClosePrice() throws Exception {
		LowestValue lowestValue = new LowestValue(new ClosePrice(data), 5);

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
		LowestValue lowestValue = new LowestValue(new ClosePrice(data), 5);
		assertEquals(2d, lowestValue.getValue(10), 0.01);
		assertEquals(3d, lowestValue.getValue(6), 0.01);
	}

	@Test
	public void testLowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		LowestValue lowestValue = new LowestValue(new ClosePrice(data), 5);
		assertEquals(1d, lowestValue.getValue(0), 0.01);
	}

	@Test
	public void testLowestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		LowestValue lowestValue = new LowestValue(new ClosePrice(data), 500);
		assertEquals(1d, lowestValue.getValue(12), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		LowestValue lowestValue = new LowestValue(new ClosePrice(data), 5);
		assertEquals(3d, (double) lowestValue.getValue(300));
	}

	@Test
	public void testGetName() {
		LowestValue lowestValue = new LowestValue(new ClosePrice(data), 5);
		assertEquals("LowestValueIndicator timeFrame: 5", lowestValue.getName());
	}
}
