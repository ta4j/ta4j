package eu.verdelhan.tailtest.indicator.tracker.bollingerbands;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.helper.StandardDeviation;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.indicator.tracker.SMA;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class BollingerBandsUpperTest {

	private TimeSeries data;

	private int timeFrame;

	private ClosePrice closePrice;

	private SMA sma;

	@Before
	public void setUp() throws Exception {
		data = new SampleTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
		timeFrame = 3;
		closePrice = new ClosePrice(data);
		sma = new SMA(closePrice, timeFrame);
	}

	@Test
	public void testBollingerBandsUpperUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsUpper bbuSMA = new BollingerBandsUpper(bbmSMA, standardDeviation);

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

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsUpper bbuSMA = new BollingerBandsUpper(bbmSMA, standardDeviation);

		assertEquals(4.96, bbuSMA.getValue(9), 0.01);
		assertEquals(4.96, bbuSMA.getValue(4), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsUpper bbuSMA = new BollingerBandsUpper(bbmSMA, standardDeviation);

		bbuSMA.getValue(data.getSize());
	}
}
