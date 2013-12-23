package eu.verdelhan.tailtest.indicator.helper;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class AverageLossIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testAverageLossUsingTimeFrame5UsingClosePrice() throws Exception {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);

		assertEquals(1d / 5d, averageLoss.getValue(5), 0.01);
		assertEquals(1d / 5d, averageLoss.getValue(6), 0.01);
		assertEquals(2d / 5d, averageLoss.getValue(7), 0.01);
		assertEquals(3d / 5d, averageLoss.getValue(8), 0.01);
		assertEquals(2d / 5d, averageLoss.getValue(9), 0.01);
		assertEquals(2d / 5d, averageLoss.getValue(10), 0.01);
		assertEquals(3d / 5d, averageLoss.getValue(11), 0.01);
		assertEquals(3d / 5d, averageLoss.getValue(12), 0.01);

	}

	@Test
	public void testAverageLossShouldWorkJumpingIndexes() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(2d / 5d, averageLoss.getValue(10), 0.01);
		assertEquals(3d / 5d, averageLoss.getValue(12), 0.01);
	}

	@Test
	public void testAverageLossMustReturnZeroWhenTheDataDoesntGain() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 4);
		assertEquals(0, averageLoss.getValue(3), 0.01);
	}

	@Test
	public void testAverageLossWhenTimeFrameIsGreaterThanIndex() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 1000);
		assertEquals(5d / data.getSize(), averageLoss.getValue(12), 0.01);
	}

	@Test
	public void testAverageGainWhenIndexIsZeroMustBeZero() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 10);
		assertEquals(0, averageLoss.getValue(0), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);
		assertEquals(3d, averageLoss.getValue(300));
	}

	@Test
	public void testGetName() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);
		assertEquals("AverageLossIndicator timeFrame: 5", averageLoss.getName());
	}

}
