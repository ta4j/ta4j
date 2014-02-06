package eu.verdelhan.ta4j.indicator.tracker.bollingerbands;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.indicator.tracker.SMA;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsMiddleTest {
	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testBollingerBandsMiddleUsingSMA() throws Exception {
		SMA sma = new SMA(new ClosePrice(data), 3);
		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);

		for (int i = 0; i < data.getSize(); i++) {
			assertThat(bbmSMA.getValue(i)).isEqualTo(sma.getValue(i));
		}
	}

	@Test
	public void testBollingerBandsLowerShouldWorkJumpingIndexes() {
		SMA sma = new SMA(new ClosePrice(data), 3);
		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);

		assertThat(bbmSMA.getValue(6)).isEqualTo(sma.getValue(6));
		assertThat(bbmSMA.getValue(0)).isEqualTo(sma.getValue(0));

	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMA sma = new SMA(new ClosePrice(data), 3);
		BollingerBandsMiddle bbmSMA = new BollingerBandsMiddle(sma);
		bbmSMA.getValue(3000);
	}
}
