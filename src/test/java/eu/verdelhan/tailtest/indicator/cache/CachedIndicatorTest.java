package net.sf.tail.indicator.cache;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.EMAIndicator;
import net.sf.tail.indicator.tracker.RSIIndicator;
import net.sf.tail.indicator.tracker.SMAIndicator;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.junit.Before;
import org.junit.Test;

public class CachedIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testIfCacheWorks() {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		Double firstTime = sma.getValue(4);
		Double seconTime = sma.getValue(4);
		assertEquals(firstTime, seconTime);
	}

	@Test
	public void testIncreaseArrayMethod() {
		double[] d = new double[200];
		Arrays.fill(d, 10);
		TimeSeries dataMax = new SampleTimeSeries(d);
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(dataMax), 100);
		assertEquals(10d, quoteSMA.getValue(105));
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 3);
		quoteSMA.getValue(13);
	}

	@Test
	public void testReallyBigCachedEMAExtendsCachedIndicator() {
		int maxIndex = 1000000;
		List<DefaultTick> ticks = new ArrayList<DefaultTick>(Collections.nCopies(maxIndex, new DefaultTick(0)));
		TimeSeries longData = new SampleTimeSeries(ticks);
		EMAIndicator quoteEMA = new EMAIndicator(new ClosePriceIndicator(longData), 10);

		quoteEMA.getValue(maxIndex - 1);

	}

	@Test
	public void testReallyCachedBigRSINotExtendsCachedIndicator() {
		int maxIndex = 1000000;
		List<DefaultTick> ticks = new ArrayList<DefaultTick>(Collections.nCopies(maxIndex, new DefaultTick(0)));
		TimeSeries longData = new SampleTimeSeries(ticks);
		RSIIndicator RSI = new RSIIndicator(new ClosePriceIndicator(longData), 10);

		RSI.getValue(maxIndex - 1);

	}

	@Test
	public void testGetName() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 3);
		assertEquals("SMAIndicator timeFrame: 3", quoteSMA.getName());
	}
}
