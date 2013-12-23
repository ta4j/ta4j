package eu.verdelhan.tailtest.indicator.helper;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class StandardDeviationIndicatorTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 0, 9 });
	}

	@Test
	public void testStandardDeviationUsingTimeFrame4UsingClosePrice() throws Exception {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);

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
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 4);

		assertEquals(0, sdv.getValue(0), 0.1);
	}

	@Test
	public void testStandardDeviationValueIndicatorValueWhenTimeFraseIs1ShouldBeZero() {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 1);
		assertEquals(0d, sdv.getValue(3), 0.1);
		assertEquals(0d, sdv.getValue(8), 0.1);
	}

	@Test
	public void testStandardDeviationUsingTimeFrame2UsingClosePrice() throws Exception {
		StandardDeviationIndicator sdv = new StandardDeviationIndicator(new ClosePriceIndicator(data), 2);

		assertEquals(0d, sdv.getValue(0), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(1), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(2), 0.1);
		assertEquals(Math.sqrt(0.5), sdv.getValue(3), 0.1);
		assertEquals(Math.sqrt(4.5), sdv.getValue(9), 0.1);
		assertEquals(Math.sqrt(40.5), sdv.getValue(10), 0.1);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		StandardDeviationIndicator quoteSDV = new StandardDeviationIndicator(new ClosePriceIndicator(data), 3);
		quoteSDV.getValue(13);
	}

	@Test
	public void testGetName() {
		StandardDeviationIndicator quoteSDV = new StandardDeviationIndicator(new ClosePriceIndicator(data), 3);
		assertEquals("StandardDeviationIndicator timeFrame: 3", quoteSDV.getName());
	}

}
