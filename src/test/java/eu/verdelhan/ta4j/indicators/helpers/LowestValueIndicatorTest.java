package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class LowestValueIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1);
	}

	@Test
	public void testLowestValueIndicatorUsingTimeFrame5UsingClosePrice() throws Exception {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);

		assertThat(lowestValue.getValue(4)).isEqualTo(1d);
		assertThat(lowestValue.getValue(5)).isEqualTo(2d);
		assertThat(lowestValue.getValue(6)).isEqualTo(3d);
		assertThat(lowestValue.getValue(7)).isEqualTo(3d);
		assertThat(lowestValue.getValue(8)).isEqualTo(3d);
		assertThat(lowestValue.getValue(9)).isEqualTo(3d);
		assertThat(lowestValue.getValue(10)).isEqualTo(2d);
		assertThat(lowestValue.getValue(11)).isEqualTo(2d);
		assertThat(lowestValue.getValue(12)).isEqualTo(2d);

	}

	@Test
	public void testLowestValueShouldWorkJumpingIndexes() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
		assertThat(lowestValue.getValue(10)).isEqualTo(2d);
		assertThat(lowestValue.getValue(6)).isEqualTo(3d);
	}

	@Test
	public void testLowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 5);
		assertThat(lowestValue.getValue(0)).isEqualTo(1d);
	}

	@Test
	public void testLowestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		LowestValueIndicator lowestValue = new LowestValueIndicator(new ClosePriceIndicator(data), 500);
		assertThat(lowestValue.getValue(12)).isEqualTo(1d);
	}
}
