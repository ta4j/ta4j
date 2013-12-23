package eu.verdelhan.tailtest.indicator.tracker;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class SMAIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testSMAUsingTimeFrame3UsingClosePrice() throws Exception {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);

		assertEquals(1.0, sma.getValue(0));
		assertEquals(1.5, sma.getValue(1));
		assertEquals(2.0, sma.getValue(2));
		assertEquals(3.0, sma.getValue(3));
		assertEquals(10.0 / 3, sma.getValue(4));
		assertEquals(11.0 / 3, sma.getValue(5));
		assertEquals(4.0, sma.getValue(6));
		assertEquals(13.0 / 3, sma.getValue(7));
		assertEquals(4.0, sma.getValue(8));
		assertEquals(10.0 / 3, sma.getValue(9));
		assertEquals(10.0 / 3, sma.getValue(10));
		assertEquals(10.0 / 3, sma.getValue(11));
		assertEquals(3.0, sma.getValue(12));

	}

	@Test
	public void testSMAWhenTimeFrameIs1ResultShouldBeIndicatorValue() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 1);
		for (int i = 0; i < data.getSize(); i++) {
			assertEquals(data.getTick(i).getClosePrice(), quoteSMA.getValue(i));
		}
	}

	@Test
	public void testSMAShouldWorkJumpingIndexes() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 3);
		assertEquals(3d, quoteSMA.getValue(12));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 3);
		assertEquals(3d, quoteSMA.getValue(13));
	}
}
