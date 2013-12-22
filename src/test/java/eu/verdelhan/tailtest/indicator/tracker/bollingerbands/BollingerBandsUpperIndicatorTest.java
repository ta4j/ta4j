package net.sf.tail.indicator.tracker.bollingerbands;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.indicator.helper.StandardDeviationIndicator;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.indicator.tracker.SMAIndicator;
import net.sf.tail.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class BollingerBandsUpperIndicatorTest {

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
	public void testBollingerBandsUpperUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

		assertEquals(1.0, bbuSMA.getValue(0), 0.01);
		assertEquals(2.91, bbuSMA.getValue(1), 0.01);
		assertEquals(4.82, bbuSMA.getValue(2), 0.01);
		assertEquals(5.82, bbuSMA.getValue(3), 0.01);
		assertEquals(4.96, bbuSMA.getValue(4), 0.01);
		assertEquals(5.29, bbuSMA.getValue(5), 0.01);
		assertEquals(6.82, bbuSMA.getValue(6), 0.01);
		assertEquals(5.96, bbuSMA.getValue(7), 0.01);
		assertEquals(6.82, bbuSMA.getValue(8), 0.01);
		assertEquals(4.96, bbuSMA.getValue(9), 0.01);

	}

	@Test
	public void testBollingerBandsUpperShouldWorkJumpingIndexes() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

		assertEquals(4.96, bbuSMA.getValue(9), 0.01);
		assertEquals(4.96, bbuSMA.getValue(4), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

		bbuSMA.getValue(data.getSize());
	}
}
