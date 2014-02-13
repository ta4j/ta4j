package eu.verdelhan.ta4j.indicators.trackers;

import eu.verdelhan.ta4j.indicators.trackers.EMA;
import eu.verdelhan.ta4j.indicators.trackers.SMA;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePrice;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class EMATest {

	private TimeSeries data;

	@Before
	public void setUp() {

		data = new MockTimeSeries(new double[] { 64.75, 63.79, 63.73, 63.73, 63.55, 63.19, 63.91, 63.85, 62.95,
				63.37, 61.33, 61.51 });
	}

	@Test
	public void testEMAUsingTimeFrame10UsingClosePrice() {
		EMA ema = new EMA(new ClosePrice(data), 10);

		assertThat(ema.getValue(9)).isEqualTo(63.65);
		assertThat(ema.getValue(10)).isEqualTo(63.23);
		assertThat(ema.getValue(11)).isEqualTo(62.91);
	}

	@Test
	public void testEMAFirstValueShouldBeEqualsToFirstDataValue() {
		EMA ema = new EMA(new ClosePrice(data), 10);

		assertThat(ema.getValue(0)).isEqualTo(64.75);
	}

	@Test
	public void testValuesLessThanTimeFrameMustBeEqualsToSMAValues() {
		EMA ema = new EMA(new ClosePrice(data), 10);
		SMA sma = new SMA(new ClosePrice(data), 10);

		for (int i = 0; i < 9; i++) {
			sma.getValue(i);
			ema.getValue(i);
			assertThat(ema.getValue(i)).isEqualTo(sma.getValue(i));
		}
	}

	@Test
	public void testEMAShouldWorkJumpingIndexes() {
		EMA ema = new EMA(new ClosePrice(data), 10);
		assertThat(ema.getValue(10)).isEqualTo(63.23);
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		EMA ema = new EMA(new ClosePrice(data), 10);
		assertThat(ema.getValue(14)).isEqualTo(3d);
	}
	
	@Test
	public void testSmallTimeFrame()
	{
		EMA ema = new EMA(new ClosePrice(data), 1);
		assertThat(ema.getValue(0)).isEqualTo(64.75d);
	}
	
}
