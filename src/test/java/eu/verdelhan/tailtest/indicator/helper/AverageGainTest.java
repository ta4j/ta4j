package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AverageGainTest {

	private TimeSeries data;

	@Before
	public void prepare() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testAverageGainUsingTimeFrame5UsingClosePrice() throws Exception {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 5);

		assertEquals(4d / 5d, averageGain.getValue(5), 0.01);
		assertEquals(4d / 5d, averageGain.getValue(6), 0.01);
		assertEquals(3d / 5d, averageGain.getValue(7), 0.01);
		assertEquals(2d / 5d, averageGain.getValue(8), 0.01);
		assertEquals(2d / 5d, averageGain.getValue(9), 0.01);
		assertEquals(2d / 5d, averageGain.getValue(10), 0.01);
		assertEquals(1d / 5d, averageGain.getValue(11), 0.01);
		assertEquals(1d / 5d, averageGain.getValue(12), 0.01);
	}

	@Test
	public void testAverageGainShouldWorkJumpingIndexes() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 5);
		assertEquals(2d / 5d, averageGain.getValue(10), 0.01);
		assertEquals(1d / 5d, averageGain.getValue(12), 0.01);
	}

	@Test
	public void testAverageGainMustReturnZeroWhenTheDataDoesntGain() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 3);
		assertEquals(0, averageGain.getValue(9), 0.01);
	}

	@Test
	public void testAverageGainWhenTimeFrameIsGreaterThanIndicatorDataShouldBeCalculatedWithDataSize() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 1000);
		assertEquals(6d / data.getSize(), averageGain.getValue(12), 0.01);
	}

	@Test
	public void testAverageGainWhenIndexIsZeroMustBeZero() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 10);
		assertEquals(0, averageGain.getValue(0), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 5);
		assertEquals((double) 3d, (double) averageGain.getValue(300));
	}
}
