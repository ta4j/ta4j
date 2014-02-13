package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.HighestValue;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class HighestValueTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testHighestValueUsingTimeFrame5UsingClosePrice() throws Exception {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);

		assertThat(highestValue.getValue(4)).isEqualTo(BigDecimal.valueOf(4));
		assertThat(highestValue.getValue(5)).isEqualTo(BigDecimal.valueOf(4));
		assertThat(highestValue.getValue(6)).isEqualTo(BigDecimal.valueOf(5));
		assertThat(highestValue.getValue(7)).isEqualTo(BigDecimal.valueOf(6));
		assertThat(highestValue.getValue(8)).isEqualTo(BigDecimal.valueOf(6));
		assertThat(highestValue.getValue(9)).isEqualTo(BigDecimal.valueOf(6));
		assertThat(highestValue.getValue(10)).isEqualTo(BigDecimal.valueOf(6));
		assertThat(highestValue.getValue(11)).isEqualTo(BigDecimal.valueOf(6));
		assertThat(highestValue.getValue(12)).isEqualTo(BigDecimal.valueOf(4));

	}

	@Test
	public void testFirstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);
		assertThat(highestValue.getValue(0)).isEqualTo(BigDecimal.ONE);
	}

	@Test
	public void testHighestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 500);
		assertThat(highestValue.getValue(12)).isEqualTo(BigDecimal.valueOf(6));
	}

	@Test
	public void testHighestValueShouldWorkJumpingIndexes() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);
		assertThat(highestValue.getValue(6)).isEqualTo(BigDecimal.valueOf(5));
		assertThat(highestValue.getValue(12)).isEqualTo(BigDecimal.valueOf(4));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);
		assertThat(highestValue.getValue(300)).isEqualTo(BigDecimal.valueOf(3));
	}
}
