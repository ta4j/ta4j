package eu.verdelhan.ta4j.indicators.helpers;

import eu.verdelhan.ta4j.indicators.helpers.LowestValue;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class LowestValueTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1 });
	}

	@Test
	public void testLowestValueIndicatorUsingTimeFrame5UsingClosePrice() throws Exception {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);

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
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);
		assertThat(lowestValue.getValue(10)).isEqualTo(BigDecimal.valueOf(2));
		assertThat(lowestValue.getValue(6)).isEqualTo(BigDecimal.valueOf(3));
	}

	@Test
	public void testLowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);
		assertThat(lowestValue.getValue(0)).isEqualTo(BigDecimal.valueOf(1));
	}

	@Test
	public void testLowestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 500);
		assertThat(lowestValue.getValue(12)).isEqualTo(BigDecimal.valueOf(1));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);
		assertThat(lowestValue.getValue(300)).isEqualTo(BigDecimal.valueOf(3));
	}
}
