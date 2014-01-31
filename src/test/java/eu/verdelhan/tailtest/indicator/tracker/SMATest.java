package eu.verdelhan.tailtest.indicator.tracker;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class SMATest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testSMAUsingTimeFrame3UsingClosePrice() throws Exception {
		SMA sma = new SMA(new ClosePrice(data), 3);

		assertEquals(1.0, (double) sma.getValue(0));
		assertEquals(1.5, (double) sma.getValue(1));
		assertEquals(2.0, (double) sma.getValue(2));
		assertEquals(3.0, (double) sma.getValue(3));
		assertEquals(10.0 / 3, (double) sma.getValue(4));
		assertEquals(11.0 / 3, (double) sma.getValue(5));
		assertEquals(4.0, (double) sma.getValue(6));
		assertEquals(13.0 / 3, (double) sma.getValue(7));
		assertEquals(4.0, (double) sma.getValue(8));
		assertEquals(10.0 / 3, (double) sma.getValue(9));
		assertEquals(10.0 / 3, (double) sma.getValue(10));
		assertEquals(10.0 / 3, (double) sma.getValue(11));
		assertEquals(3.0, (double) sma.getValue(12));

	}

	@Test
	public void testSMAWhenTimeFrameIs1ResultShouldBeIndicatorValue() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 1);
		for (int i = 0; i < data.getSize(); i++) {
			assertEquals(data.getTick(i).getClosePrice(), (double) quoteSMA.getValue(i));
		}
	}

	@Test
	public void testSMAShouldWorkJumpingIndexes() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 3);
		assertEquals(3d, (double) quoteSMA.getValue(12));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 3);
		assertEquals(3d, (double) quoteSMA.getValue(13));
	}
}
