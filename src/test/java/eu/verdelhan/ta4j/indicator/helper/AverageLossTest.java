package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AverageLossTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testAverageLossUsingTimeFrame5UsingClosePrice() throws Exception {
		AverageLoss averageLoss = new AverageLoss(new ClosePrice(data), 5);

		assertThat(averageLoss.getValue(5)).isEqualTo(1d / 5d);
		assertThat(averageLoss.getValue(6)).isEqualTo(1d / 5d);
		assertThat(averageLoss.getValue(7)).isEqualTo(2d / 5d);
		assertThat(averageLoss.getValue(8)).isEqualTo(3d / 5d);
		assertThat(averageLoss.getValue(9)).isEqualTo(2d / 5d);
		assertThat(averageLoss.getValue(10)).isEqualTo(2d / 5d);
		assertThat(averageLoss.getValue(11)).isEqualTo(3d / 5d);
		assertThat(averageLoss.getValue(12)).isEqualTo(3d / 5d);

	}

	@Test
	public void testAverageLossShouldWorkJumpingIndexes() {
		AverageLoss averageLoss = new AverageLoss(new ClosePrice(data), 5);
		assertThat(averageLoss.getValue(10)).isEqualTo(2d / 5d);
		assertThat(averageLoss.getValue(12)).isEqualTo(3d / 5d);
	}

	@Test
	public void testAverageLossMustReturnZeroWhenTheDataDoesntGain() {
		AverageLoss averageLoss = new AverageLoss(new ClosePrice(data), 4);
		assertThat(averageLoss.getValue(3)).isEqualTo(0);
	}

	@Test
	public void testAverageLossWhenTimeFrameIsGreaterThanIndex() {
		AverageLoss averageLoss = new AverageLoss(new ClosePrice(data), 1000);
		assertEquals(5d / data.getSize(), averageLoss.getValue(12), 0.01);
	}

	@Test
	public void testAverageGainWhenIndexIsZeroMustBeZero() {
		AverageLoss averageLoss = new AverageLoss(new ClosePrice(data), 10);
		assertThat(averageLoss.getValue(0)).isEqualTo(0);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		AverageLoss averageLoss = new AverageLoss(new ClosePrice(data), 5);
		assertEquals((double) 3d, (double) averageLoss.getValue(300));
	}
}
