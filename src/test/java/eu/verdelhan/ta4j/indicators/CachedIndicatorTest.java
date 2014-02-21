package eu.verdelhan.ta4j.indicators;

import eu.verdelhan.ta4j.Tick;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.EMAIndicator;
import eu.verdelhan.ta4j.indicators.trackers.RSIIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTick;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class CachedIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
	}

	@Test
	public void testIfCacheWorks() {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		Double firstTime = sma.getValue(4);
		Double seconTime = sma.getValue(4);
		assertThat(seconTime).isEqualTo(firstTime);
	}

	@Test
	public void testIncreaseArrayMethod() {
		double[] d = new double[200];
		Arrays.fill(d, 10);
		TimeSeries dataMax = new MockTimeSeries(d);
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(dataMax), 100);
		assertThat(quoteSMA.getValue(105)).isEqualTo((double) 10d);
	}

	@Test
	public void testReallyBigCachedEMAExtendsCachedIndicator() {
		int maxIndex = 1000000;
		List<Tick> ticks = new ArrayList<Tick>(Collections.nCopies(maxIndex, new MockTick(0)));
		TimeSeries longData = new MockTimeSeries(ticks);
		EMAIndicator quoteEMA = new EMAIndicator(new ClosePriceIndicator(longData), 10);

		quoteEMA.getValue(maxIndex - 1);

	}

	@Test
	public void testReallyCachedBigRSINotExtendsCachedIndicator() {
		int maxIndex = 1000000;
		List<Tick> ticks = new ArrayList<Tick>(Collections.nCopies(maxIndex, new MockTick(0)));
		TimeSeries longData = new MockTimeSeries(ticks);
		RSIIndicator RSI = new RSIIndicator(new ClosePriceIndicator(longData), 10);

		RSI.getValue(maxIndex - 1);

	}
}
