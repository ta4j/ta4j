package eu.verdelhan.tailtest.indicator.tracker.bollingerbands;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.simple.ClosePriceIndicator;
import eu.verdelhan.tailtest.indicator.tracker.SMAIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class BollingerBandsMiddleIndicatorTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testBollingerBandsMiddleUsingSMA() throws Exception {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);

		for (int i = 0; i < data.getSize(); i++) {
			assertEquals(sma.getValue(i), bbmSMA.getValue(i));
		}
	}

	@Test
	public void testBollingerBandsLowerShouldWorkJumpingIndexes() {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);

		assertEquals(sma.getValue(6), bbmSMA.getValue(6));
		assertEquals(sma.getValue(0), bbmSMA.getValue(0));

	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		bbmSMA.getValue(3000);
	}
}
