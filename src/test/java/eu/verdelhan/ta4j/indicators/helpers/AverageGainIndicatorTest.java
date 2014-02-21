package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AverageGainIndicatorTest {

	private TimeSeries data;

	@Before
	public void prepare() throws Exception {
		data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
	}

	@Test
	public void testAverageGainUsingTimeFrame5UsingClosePrice() throws Exception {
		AverageGainIndicator averageGain = new AverageGainIndicator(new ClosePriceIndicator(data), 5);

		assertThat(averageGain.getValue(5)).isEqualTo(4d / 5d);
		assertThat(averageGain.getValue(6)).isEqualTo(4d / 5d);
		assertThat(averageGain.getValue(7)).isEqualTo(3d / 5d);
		assertThat(averageGain.getValue(8)).isEqualTo(2d / 5d);
		assertThat(averageGain.getValue(9)).isEqualTo(2d / 5d);
		assertThat(averageGain.getValue(10)).isEqualTo(2d / 5d);
		assertThat(averageGain.getValue(11)).isEqualTo(1d / 5d);
		assertThat(averageGain.getValue(12)).isEqualTo(1d / 5d);
	}

	@Test
	public void testAverageGainShouldWorkJumpingIndexes() {
		AverageGainIndicator averageGain = new AverageGainIndicator(new ClosePriceIndicator(data), 5);
		assertThat(averageGain.getValue(10)).isEqualTo(2d / 5d);
		assertThat(averageGain.getValue(12)).isEqualTo(1d / 5d);
	}

	@Test
	public void testAverageGainMustReturnZeroWhenTheDataDoesntGain() {
		AverageGainIndicator averageGain = new AverageGainIndicator(new ClosePriceIndicator(data), 3);
		assertThat(averageGain.getValue(9)).isEqualTo(0);
	}

	@Test
	public void testAverageGainWhenTimeFrameIsGreaterThanIndicatorDataShouldBeCalculatedWithDataSize() {
		AverageGainIndicator averageGain = new AverageGainIndicator(new ClosePriceIndicator(data), 1000);
		assertThat(averageGain.getValue(12)).isEqualTo(6d / data.getSize());
	}

	@Test
	public void testAverageGainWhenIndexIsZeroMustBeZero() {
		AverageGainIndicator averageGain = new AverageGainIndicator(new ClosePriceIndicator(data), 10);
		assertThat(averageGain.getValue(0)).isEqualTo(0);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		AverageGainIndicator averageGain = new AverageGainIndicator(new ClosePriceIndicator(data), 5);
		assertThat(averageGain.getValue(300)).isEqualTo((double) 3d);
	}
}
