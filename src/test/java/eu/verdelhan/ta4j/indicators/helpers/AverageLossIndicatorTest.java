package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.AverageLossIndicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AverageLossIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testAverageLossUsingTimeFrame5UsingClosePrice() throws Exception {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);

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
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);
		assertThat(averageLoss.getValue(10)).isEqualTo(2d / 5d);
		assertThat(averageLoss.getValue(12)).isEqualTo(3d / 5d);
	}

	@Test
	public void testAverageLossMustReturnZeroWhenTheDataDoesntGain() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 4);
		assertThat(averageLoss.getValue(3)).isEqualTo(0);
	}

	@Test
	public void testAverageLossWhenTimeFrameIsGreaterThanIndex() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 1000);
		assertThat(averageLoss.getValue(12)).isEqualTo(5d / data.getSize());
	}

	@Test
	public void testAverageGainWhenIndexIsZeroMustBeZero() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 10);
		assertThat(averageLoss.getValue(0)).isEqualTo(0);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		AverageLossIndicator averageLoss = new AverageLossIndicator(new ClosePriceIndicator(data), 5);
		assertThat(averageLoss.getValue(300)).isEqualTo((double) 3d);
	}
}
