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

public class BollingerBandsLowerIndicatorTest {

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
	public void testBollingerBandsLowerUsingSMAAndStandardDeviation() throws Exception {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

		assertThat(bblSMA.getValue(0)).isEqualTo(1d);
		assertThat(bblSMA.getValue(1)).isEqualTo(0.08, TATestsUtils.SHORT_OFFSET);
		assertThat(bblSMA.getValue(2)).isEqualTo(-0.82, TATestsUtils.SHORT_OFFSET);
		assertThat(bblSMA.getValue(3)).isEqualTo(0.17, TATestsUtils.SHORT_OFFSET);
		assertThat(bblSMA.getValue(4)).isEqualTo(1.70, TATestsUtils.SHORT_OFFSET);
		assertThat(bblSMA.getValue(5)).isEqualTo(2.03, TATestsUtils.SHORT_OFFSET);
		assertThat(bblSMA.getValue(6)).isEqualTo(1.17, TATestsUtils.SHORT_OFFSET);
	}

	@Test
	public void testBollingerBandsLowerShouldWorkJumpingIndexes() {

		BollingerBandsMiddleIndicator bbmSMA = new BollingerBandsMiddleIndicator(sma);
		StandardDeviationIndicator standardDeviation = new StandardDeviationIndicator(closePrice, timeFrame);
		BollingerBandsLowerIndicator bblSMA = new BollingerBandsLowerIndicator(bbmSMA, standardDeviation);

		assertThat(bblSMA.getValue(6)).isEqualTo(1.17, TATestsUtils.SHORT_OFFSET);
		assertThat(bblSMA.getValue(1)).isEqualTo(0.08, TATestsUtils.SHORT_OFFSET);
	}
}
