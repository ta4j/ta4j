package eu.verdelhan.ta4j.indicator.simple;

import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class VolumeTest {
	private Volume volumeIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new MockTimeSeries();
		volumeIndicator = new Volume(timeSeries);
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
