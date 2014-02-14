package eu.verdelhan.ta4j.indicators.simple;

import eu.verdelhan.ta4j.indicators.simple.VolumeIndicator;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class VolumeIndicatorTest {
	private VolumeIndicator volumeIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		volumeIndicator = new VolumeIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickVolume() {
		for (int i = 0; i < 10; i++) {
			assertThat(timeSeries.getTick(i).getVolume()).isEqualTo(volumeIndicator.getValue(i));
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		volumeIndicator.getValue(10);
	}
}
