package eu.verdelhan.ta4j.indicator.cache;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.indicator.tracker.EMA;
import eu.verdelhan.ta4j.indicator.tracker.RSI;
import eu.verdelhan.ta4j.indicator.tracker.SMA;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class CachedIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testIfCacheWorks() {
		SMA sma = new SMA(new ClosePrice(data), 3);
		Double firstTime = sma.getValue(4);
		Double seconTime = sma.getValue(4);
		assertEquals(firstTime, seconTime);
	}

	@Test
	public void testIncreaseArrayMethod() {
		double[] d = new double[200];
		Arrays.fill(d, 10);
		TimeSeries dataMax = new MockTimeSeries(d);
		SMA quoteSMA = new SMA(new ClosePrice(dataMax), 100);
		assertEquals((double) 10d, (double) quoteSMA.getValue(105));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 3);
		quoteSMA.getValue(13);
	}

	@Test
	public void testReallyBigCachedEMAExtendsCachedIndicator() {
		int maxIndex = 1000000;
		List<Tick> ticks = new ArrayList<Tick>(Collections.nCopies(maxIndex, new MockTick(0)));
		TimeSeries longData = new MockTimeSeries(ticks);
		EMA quoteEMA = new EMA(new ClosePrice(longData), 10);

		quoteEMA.getValue(maxIndex - 1);

	}

	@Test
	public void testReallyCachedBigRSINotExtendsCachedIndicator() {
		int maxIndex = 1000000;
		List<Tick> ticks = new ArrayList<Tick>(Collections.nCopies(maxIndex, new MockTick(0)));
		TimeSeries longData = new MockTimeSeries(ticks);
		RSI RSI = new RSI(new ClosePrice(longData), 10);

		RSI.getValue(maxIndex - 1);

	}
}
