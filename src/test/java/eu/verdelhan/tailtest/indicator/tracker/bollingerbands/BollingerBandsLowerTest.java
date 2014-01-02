package eu.verdelhan.tailtest.indicator.tracker.bollingerbands;

import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.indicator.helper.StandardDeviation;
import eu.verdelhan.tailtest.indicator.simple.ClosePrice;
import eu.verdelhan.tailtest.indicator.tracker.SMA;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsLowerTest {

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
	public void testBollingerBandsLowerUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsLower bblSMA = new BollingerBandsLower(bbmSMA, standardDeviation);

		assertEquals(1d, (double) bblSMA.getValue(0));
		assertEquals(0.08, bblSMA.getValue(1), 0.01);
		assertEquals(-0.82, bblSMA.getValue(2), 0.01);
		assertEquals(0.17, bblSMA.getValue(3), 0.01);
		assertEquals(1.70, bblSMA.getValue(4), 0.01);
		assertEquals(2.03, bblSMA.getValue(5), 0.01);
		assertEquals(1.17, bblSMA.getValue(6), 0.01);
	}

	@Test
	public void testBollingerBandsLowerShouldWorkJumpingIndexes() {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsLower bblSMA = new BollingerBandsLower(bbmSMA, standardDeviation);

		assertEquals(1.17, bblSMA.getValue(6), 0.01);
		assertEquals(0.08, bblSMA.getValue(1), 0.01);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsLower bblSMA = new BollingerBandsLower(bbmSMA, standardDeviation);

		bblSMA.getValue(data.getSize());
	}
}
