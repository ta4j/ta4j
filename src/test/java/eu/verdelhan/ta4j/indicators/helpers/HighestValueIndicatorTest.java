package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class HighestValueIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testHighestValueUsingTimeFrame5UsingClosePrice() throws Exception {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);

		assertThat(highestValue.getValue(4)).isEqualTo(4d);
		assertThat(highestValue.getValue(5)).isEqualTo(4d);
		assertThat(highestValue.getValue(6)).isEqualTo(5d);
		assertThat(highestValue.getValue(7)).isEqualTo(6d);
		assertThat(highestValue.getValue(8)).isEqualTo(6d);
		assertThat(highestValue.getValue(9)).isEqualTo(6d);
		assertThat(highestValue.getValue(10)).isEqualTo(6d);
		assertThat(highestValue.getValue(11)).isEqualTo(6d);
		assertThat(highestValue.getValue(12)).isEqualTo(4d);

	}

	@Test
	public void testFirstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertThat(highestValue.getValue(0)).isEqualTo(1);
	}

	@Test
	public void testHighestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 500);
		assertThat(highestValue.getValue(12)).isEqualTo(6d);
	}

	@Test
	public void testHighestValueShouldWorkJumpingIndexes() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertThat(highestValue.getValue(6)).isEqualTo(5d);
		assertThat(highestValue.getValue(12)).isEqualTo(4d);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		HighestValueIndicator highestValue = new HighestValueIndicator(new ClosePriceIndicator(data), 5);
		assertThat(highestValue.getValue(300)).isEqualTo(3d);
	}
}
