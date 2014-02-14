package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.indicators.trackers.bollingerbands.BollingerBandsMiddleIndicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsMiddleIndicatorTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testBollingerBandsMiddleUsingSMA() throws Exception {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);

		for (int i = 0; i < data.getSize(); i++) {
			assertThat(bbmSMA.getValue(i)).isEqualTo(sma.getValue(i));
		}
	}

	@Test
	public void testBollingerBandsLowerShouldWorkJumpingIndexes() {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);

		assertThat(bbmSMA.getValue(6)).isEqualTo(sma.getValue(6));
		assertThat(bbmSMA.getValue(0)).isEqualTo(sma.getValue(0));

	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);
		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		bbmSMA.getValue(3000);
	}
}
