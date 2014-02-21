package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class SMAIndicatorTest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2);
	}

	@Test
	public void testSMAUsingTimeFrame3UsingClosePrice() throws Exception {
		SMAIndicator sma = new SMAIndicator(new ClosePriceIndicator(data), 3);

		assertThat(sma.getValue(0)).isEqualTo(1.0);
		assertThat(sma.getValue(1)).isEqualTo(1.5);
		assertThat(sma.getValue(2)).isEqualTo(2.0);
		assertThat(sma.getValue(3)).isEqualTo(3.0);
		assertThat(sma.getValue(4)).isEqualTo(10.0 / 3);
		assertThat(sma.getValue(5)).isEqualTo(11.0 / 3);
		assertThat(sma.getValue(6)).isEqualTo(4.0);
		assertThat(sma.getValue(7)).isEqualTo(13.0 / 3);
		assertThat(sma.getValue(8)).isEqualTo(4.0);
		assertThat(sma.getValue(9)).isEqualTo(10.0 / 3);
		assertThat(sma.getValue(10)).isEqualTo(10.0 / 3);
		assertThat(sma.getValue(11)).isEqualTo(10.0 / 3);
		assertThat(sma.getValue(12)).isEqualTo(3.0);

	}

	@Test
	public void testSMAWhenTimeFrameIs1ResultShouldBeIndicatorValue() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 1);
		for (int i = 0; i < data.getSize(); i++) {
			assertThat(quoteSMA.getValue(i)).isEqualTo(data.getTick(i).getClosePrice());
		}
	}

	@Test
	public void testSMAShouldWorkJumpingIndexes() {
		SMAIndicator quoteSMA = new SMAIndicator(new ClosePriceIndicator(data), 3);
		assertThat(quoteSMA.getValue(12)).isEqualTo(3d);
	}
}
