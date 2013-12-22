package net.sf.tail.indicator.tracker.bollingerbands;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.helper.StandardDeviationIndicator;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.SMAIndicator;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class BollingerBandsLowerIndicatorTest {

	private TimeSeries data;

	private int timeFrame;

	private ClosePriceIndicator closePrice;

	private SMAIndicator sma;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
		timeFrame = 3;
		closePrice = new ClosePriceIndicator(data);
		sma = new SMAIndicator(closePrice, timeFrame);
	}

	@Test
	public void testBollingerBandsLowerUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

		assertEquals(1d, bblSMA.getValue(0));
		assertEquals(0.08, bblSMA.getValue(1), 0.01);
		assertEquals(-0.82, bblSMA.getValue(2), 0.01);
		assertEquals(0.17, bblSMA.getValue(3), 0.01);
		assertEquals(1.70, bblSMA.getValue(4), 0.01);
		assertEquals(2.03, bblSMA.getValue(5), 0.01);
		assertEquals(1.17, bblSMA.getValue(6), 0.01);
	}

	@Test
	public void testBollingerBandsLowerShouldWorkJumpingIndexes() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

		assertEquals(1.17, bblSMA.getValue(6), 0.01);
		assertEquals(0.08, bblSMA.getValue(1), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

		bblSMA.getValue(data.getSize());
	}
}
