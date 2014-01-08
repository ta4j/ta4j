package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class LowestValueTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 2, 4, 3, 1 });
	}

	@Test
	public void testLowestValueIndicatorUsingTimeFrame5UsingClosePrice() throws Exception {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);

		assertEquals(BigDecimal.valueOf(1), lowestValue.getValue(4));
		assertEquals(BigDecimal.valueOf(2), lowestValue.getValue(5));
		assertEquals(BigDecimal.valueOf(3), lowestValue.getValue(6));
		assertEquals(BigDecimal.valueOf(3), lowestValue.getValue(7));
		assertEquals(BigDecimal.valueOf(3), lowestValue.getValue(8));
		assertEquals(BigDecimal.valueOf(3), lowestValue.getValue(9));
		assertEquals(BigDecimal.valueOf(2), lowestValue.getValue(10));
		assertEquals(BigDecimal.valueOf(2), lowestValue.getValue(11));
		assertEquals(BigDecimal.valueOf(2), lowestValue.getValue(12));

	}

	@Test
	public void testLowestValueShouldWorkJumpingIndexes() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);
		assertEquals(BigDecimal.valueOf(2), lowestValue.getValue(10));
		assertEquals(BigDecimal.valueOf(3), lowestValue.getValue(6));
	}

	@Test
	public void testLowestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);
		assertEquals(BigDecimal.valueOf(1), lowestValue.getValue(0));
	}

	@Test
	public void testLowestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 500);
		assertEquals(BigDecimal.valueOf(1), lowestValue.getValue(12));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		LowestValue<BigDecimal> lowestValue = new LowestValue<BigDecimal>(new ClosePrice(data), 5);
		assertEquals(BigDecimal.valueOf(3), lowestValue.getValue(300));
	}
}
