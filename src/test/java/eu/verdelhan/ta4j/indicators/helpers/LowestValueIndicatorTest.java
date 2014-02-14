package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.LowestValueIndicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class LowestValueIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1 });
	}

	@Test
	public void testLowestValueIndicatorUsingTimeFrame5UsingClosePrice() throws Exception {
		LowestValueIndicator<BigDecimal> lowestValue = new LowestValueIndicator<BigDecimal>(new ClosePriceIndicator(data), 5);

		assertThat(lowestValue.getValue(4)).isEqualTo(BigDecimal.valueOf(1));
		assertThat(lowestValue.getValue(5)).isEqualTo(BigDecimal.valueOf(2));
		assertThat(lowestValue.getValue(6)).isEqualTo(BigDecimal.valueOf(3));
		assertThat(lowestValue.getValue(7)).isEqualTo(BigDecimal.valueOf(3));
		assertThat(lowestValue.getValue(8)).isEqualTo(BigDecimal.valueOf(3));
		assertThat(lowestValue.getValue(9)).isEqualTo(BigDecimal.valueOf(3));
		assertThat(lowestValue.getValue(10)).isEqualTo(BigDecimal.valueOf(2));
		assertThat(lowestValue.getValue(11)).isEqualTo(BigDecimal.valueOf(2));
		assertThat(lowestValue.getValue(12)).isEqualTo(BigDecimal.valueOf(2));

	}

	@Test
	public void testLowestValueShouldWorkJumpingIndexes() {
		LowestValueIndicator<BigDecimal> lowestValue = new LowestValueIndicator<BigDecimal>(new ClosePriceIndicator(data), 5);
		assertThat(lowestValue.getValue(10)).isEqualTo(BigDecimal.valueOf(2));
		assertThat(lowestValue.getValue(6)).isEqualTo(BigDecimal.valueOf(3));
	}

	@Test
	public void testLowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		LowestValueIndicator<BigDecimal> lowestValue = new LowestValueIndicator<BigDecimal>(new ClosePriceIndicator(data), 5);
		assertThat(lowestValue.getValue(0)).isEqualTo(BigDecimal.valueOf(1));
	}

	@Test
	public void testLowestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		LowestValueIndicator<BigDecimal> lowestValue = new LowestValueIndicator<BigDecimal>(new ClosePriceIndicator(data), 500);
		assertThat(lowestValue.getValue(12)).isEqualTo(BigDecimal.valueOf(1));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		LowestValueIndicator<BigDecimal> lowestValue = new LowestValueIndicator<BigDecimal>(new ClosePriceIndicator(data), 5);
		assertThat(lowestValue.getValue(300)).isEqualTo(BigDecimal.valueOf(3));
	}
}
