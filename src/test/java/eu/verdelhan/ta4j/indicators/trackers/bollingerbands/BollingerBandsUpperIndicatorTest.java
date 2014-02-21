package eu.verdelhan.ta4j.indicators.trackers.bollingerbands;

import eu.verdelhan.ta4j.TATestsUtils;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.helpers.StandardDeviationIndicator;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.indicators.trackers.SMAIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class BollingerBandsUpperIndicatorTest {

	private TimeSeries data;

	private int timeFrame;

	private ClosePriceIndicator closePrice;

	private SMAIndicator sma;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
		timeFrame = 3;
		closePrice = new ClosePriceIndicator(data);
		sma = new SMAIndicator(closePrice, timeFrame);
	}

	@Test
	public void testBollingerBandsUpperUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

		assertThat(bbuSMA.getValue(0)).isEqualTo(1.0);
		assertThat(bbuSMA.getValue(1)).isEqualTo(2.91, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(2)).isEqualTo(4.82, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(3)).isEqualTo(5.82, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(4)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(5)).isEqualTo(5.29, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(6)).isEqualTo(6.82, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(7)).isEqualTo(5.96, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(8)).isEqualTo(6.82, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(9)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);

	}

	@Test
	public void testBollingerBandsUpperShouldWorkJumpingIndexes() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

		assertThat(bbuSMA.getValue(9)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);
		assertThat(bbuSMA.getValue(4)).isEqualTo(4.96, TATestsUtils.SHORT_OFFSET);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsUpperIndicator bbuSMA = new BollingerBandsUpperIndicator(bbmSMA, standardDeviation);

		bbuSMA.getValue(data.getSize());
	}
}
