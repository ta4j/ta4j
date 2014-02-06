package eu.verdelhan.ta4j.indicator.tracker;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicator.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class SMATest {

	private TimeSeries data;

	@Before
	public void setUp() throws Exception {
		data = new MockTimeSeries(new double[] { 1, 2, 3, 4, 3, 4, 5, 4, 3, 3, 4, 3, 2 });
	}

	@Test
	public void testSMAUsingTimeFrame3UsingClosePrice() throws Exception {
		SMA sma = new SMA(new ClosePrice(data), 3);

		assertThat((double) sma.getValue(0)).isEqualTo(1.0);
		assertThat((double) sma.getValue(1)).isEqualTo(1.5);
		assertThat((double) sma.getValue(2)).isEqualTo(2.0);
		assertThat((double) sma.getValue(3)).isEqualTo(3.0);
		assertThat((double) sma.getValue(4)).isEqualTo(10.0 / 3);
		assertThat((double) sma.getValue(5)).isEqualTo(11.0 / 3);
		assertThat((double) sma.getValue(6)).isEqualTo(4.0);
		assertThat((double) sma.getValue(7)).isEqualTo(13.0 / 3);
		assertThat((double) sma.getValue(8)).isEqualTo(4.0);
		assertThat((double) sma.getValue(9)).isEqualTo(10.0 / 3);
		assertThat((double) sma.getValue(10)).isEqualTo(10.0 / 3);
		assertThat((double) sma.getValue(11)).isEqualTo(10.0 / 3);
		assertThat((double) sma.getValue(12)).isEqualTo(3.0);

	}

	@Test
	public void testSMAWhenTimeFrameIs1ResultShouldBeIndicatorValue() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 1);
		for (int i = 0; i < data.getSize(); i++) {
			assertThat((double) quoteSMA.getValue(i)).isEqualTo(data.getTick(i).getClosePrice());
		}
	}

	@Test
	public void testSMAShouldWorkJumpingIndexes() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 3);
		assertThat((double) quoteSMA.getValue(12)).isEqualTo(3d);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		SMA quoteSMA = new SMA(new ClosePrice(data), 3);
		assertThat((double) quoteSMA.getValue(13)).isEqualTo(3d);
	}
}
