package eu.verdelhan.ta4j.indicator.helper;

import eu.verdelhan.ta4j.indicator.helper.StandardDeviation;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class StandardDeviationTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9 });
	}

	@Test
	public void testStandardDeviationUsingTimeFrame4UsingClosePrice() throws Exception {
		StandardDeviation sdv = new StandardDeviation(new ClosePrice(data), 4);

		assertEquals(0d, sdv.getValue(0), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(1), 0.1);
		assertEquals(Math.sqrt(2.0), sdv.getValue(2), 0.1);
		assertEquals(Math.sqrt(5.0), sdv.getValue(3), 0.1);
		assertEquals(Math.sqrt(2.0), sdv.getValue(4), 0.1);
		assertEquals(1, sdv.getValue(5), 0.1);
		assertEquals(Math.sqrt(2.0), sdv.getValue(6), 0.1);
		assertEquals(Math.sqrt(2.0), sdv.getValue(7), 0.1);
		assertEquals(Math.sqrt(2.0), sdv.getValue(8), 0.1);
		assertEquals(Math.sqrt(14.0), sdv.getValue(9), 0.1);
		assertEquals(Math.sqrt(42.0), sdv.getValue(10), 0.1);

	}

	@Test
	public void testFirstValueShouldBeZero() throws Exception {
		StandardDeviation sdv = new StandardDeviation(new ClosePrice(data), 4);

		assertEquals(0, sdv.getValue(0), 0.1);
	}

	@Test
	public void testStandardDeviationValueIndicatorValueWhenTimeFraseIs1ShouldBeZero() {
		StandardDeviation sdv = new StandardDeviation(new ClosePrice(data), 1);
		assertEquals(0d, sdv.getValue(3), 0.1);
		assertEquals(0d, sdv.getValue(8), 0.1);
	}

	@Test
	public void testStandardDeviationUsingTimeFrame2UsingClosePrice() throws Exception {
		StandardDeviation sdv = new StandardDeviation(new ClosePrice(data), 2);

		assertEquals(0d, sdv.getValue(0), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(1), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(2), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(3), 0.1);
		assertEquals(Math.sqrt(4.5), sdv.getValue(9), 0.1);
		assertEquals(Math.sqrt(40.5), sdv.getValue(10), 0.1);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		StandardDeviation quoteSDV = new StandardDeviation(new ClosePrice(data), 3);
		quoteSDV.getValue(13);
	}
}
