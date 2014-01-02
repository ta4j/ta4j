package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class HighestValueTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testHighestValueUsingTimeFrame5UsingClosePrice() throws Exception {
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 5);

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
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 5);
		assertEquals(1d, highestValue.getValue(0), 0.01);
	}

	@Test
	public void testHighestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 500);
		assertEquals(6d, highestValue.getValue(12), 0.01);
	}

	@Test
	public void testHighestValueShouldWorkJumpingIndexes() {
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 5);
		assertEquals(5d, highestValue.getValue(6), 0.01);
		assertEquals(4, highestValue.getValue(12), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 5);
		assertEquals(3d, (double) highestValue.getValue(300));
	}

	@Test
	public void testGetName() {
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 5);
		assertEquals("HighestValueIndicator timeFrame: 5", highestValue.getName());
	}
}
