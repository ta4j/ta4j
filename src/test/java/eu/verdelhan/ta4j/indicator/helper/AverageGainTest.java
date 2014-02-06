package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AverageGainTest {

	private TimeSeries data;

	@Before
	public void prepare() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testAverageGainUsingTimeFrame5UsingClosePrice() throws Exception {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 5);

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
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 5);
		assertThat(averageGain.getValue(10)).isEqualTo(2d / 5d);
		assertThat(averageGain.getValue(12)).isEqualTo(1d / 5d);
	}

	@Test
	public void testAverageGainMustReturnZeroWhenTheDataDoesntGain() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 3);
		assertThat(averageGain.getValue(9)).isEqualTo(0);
	}

	@Test
	public void testAverageGainWhenTimeFrameIsGreaterThanIndicatorDataShouldBeCalculatedWithDataSize() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 1000);
		assertThat(averageGain.getValue(12)).isEqualTo(6d / data.getSize());
	}

	@Test
	public void testAverageGainWhenIndexIsZeroMustBeZero() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 10);
		assertThat(averageGain.getValue(0)).isEqualTo(0);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		AverageGain averageGain = new AverageGain(new ClosePrice(data), 5);
		assertThat((double) averageGain.getValue(300)).isEqualTo((double) 3d);
	}
}
