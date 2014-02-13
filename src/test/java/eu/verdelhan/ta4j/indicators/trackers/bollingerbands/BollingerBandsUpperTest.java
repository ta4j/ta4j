package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.indicators.trackers.bollingerbands.BollingerBandsMiddle;
import eu.verdelhan.ta4j.indicators.trackers.bollingerbands.BollingerBandsUpper;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.StandardDeviation;
import eu.verdelhan.ta4j.indicators.simple.ClosePrice;
import eu.verdelhan.ta4j.indicators.trackers.SMA;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsUpperTest {

	private TimeSeries data;

	private int timeFrame;

	private ClosePrice closePrice;

	private SMA sma;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
		timeFrame = 3;
		closePrice = new ClosePrice(data);
		sma = new SMA(closePrice, timeFrame);
	}

	@Test
	public void testBollingerBandsUpperUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsUpper bbuSMA = new BollingerBandsUpper(bbmSMA, standardDeviation);

		assertThat(bbuSMA.getValue(0)).isEqualTo(1.0);
		assertThat(bbuSMA.getValue(1)).isEqualTo(2.91);
		assertThat(bbuSMA.getValue(2)).isEqualTo(4.82);
		assertThat(bbuSMA.getValue(3)).isEqualTo(5.82);
		assertThat(bbuSMA.getValue(4)).isEqualTo(4.96);
		assertThat(bbuSMA.getValue(5)).isEqualTo(5.29);
		assertThat(bbuSMA.getValue(6)).isEqualTo(6.82);
		assertThat(bbuSMA.getValue(7)).isEqualTo(5.96);
		assertThat(bbuSMA.getValue(8)).isEqualTo(6.82);
		assertThat(bbuSMA.getValue(9)).isEqualTo(4.96);

	}

	@Test
	public void testBollingerBandsUpperShouldWorkJumpingIndexes() {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsUpper bbuSMA = new BollingerBandsUpper(bbmSMA, standardDeviation);

		assertThat(bbuSMA.getValue(9)).isEqualTo(4.96);
		assertThat(bbuSMA.getValue(4)).isEqualTo(4.96);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		StandardDeviation standardDeviation = new StandardDeviation(closePrice, timeFrame);
		BollingerBandsUpper bbuSMA = new BollingerBandsUpper(bbmSMA, standardDeviation);

		bbuSMA.getValue(data.getSize());
	}
}
