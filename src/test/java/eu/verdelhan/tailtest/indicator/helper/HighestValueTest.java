package eu.verdelhan.tailtest.indicator.helper;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import java.math.BigDecimal;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class HighestValueTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 6, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testHighestValueUsingTimeFrame5UsingClosePrice() throws Exception {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);

		assertEquals(BigDecimal.valueOf(4), highestValue.getValue(4));
		assertEquals(BigDecimal.valueOf(4), highestValue.getValue(5));
		assertEquals(BigDecimal.valueOf(5), highestValue.getValue(6));
		assertEquals(BigDecimal.valueOf(6), highestValue.getValue(7));
		assertEquals(BigDecimal.valueOf(6), highestValue.getValue(8));
		assertEquals(BigDecimal.valueOf(6), highestValue.getValue(9));
		assertEquals(BigDecimal.valueOf(6), highestValue.getValue(10));
		assertEquals(BigDecimal.valueOf(6), highestValue.getValue(11));
		assertEquals(BigDecimal.valueOf(4), highestValue.getValue(12));

	}

	@Test
	public void testFirstHighestValueIndicatorValueShouldBeEqualsToFirstDataValue() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);
		assertEquals(BigDecimal.ONE, highestValue.getValue(0));
	}

	@Test
	public void testHighestValueIndicatorWhenTimeFrameIsGreaterThanIndex() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 500);
		assertEquals(BigDecimal.valueOf(6), highestValue.getValue(12));
	}

	@Test
	public void testHighestValueShouldWorkJumpingIndexes() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);
		assertEquals(BigDecimal.valueOf(5), highestValue.getValue(6));
		assertEquals(BigDecimal.valueOf(4), highestValue.getValue(12));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		HighestValue<BigDecimal> highestValue = new HighestValue<BigDecimal>(new ClosePrice(data), 5);
		assertEquals(BigDecimal.valueOf(3), highestValue.getValue(300));
	}

	@Test
	public void testGetName() {
		HighestValue highestValue = new HighestValue(new ClosePrice(data), 5);
		assertEquals("HighestValueIndicator timeFrame: 5", highestValue.getName());
	}
}
