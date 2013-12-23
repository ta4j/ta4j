package eu.verdelhan.tailtest.indicator.simple;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Before;
import org.junit.Test;

public class VolumeIndicatorTest {
	private VolumeIndicator volumeIndicator;

	TimeSeries timeSeries;

	@Before
	public void setUp() {
		timeSeries = new SampleTimeSeries();
		volumeIndicator = new VolumeIndicator(timeSeries);
	}

	@Test
	public void testIndicatorShouldRetrieveTickVolume() {
		for (int i = 0; i < 10; i++) {
			assertEquals(volumeIndicator.getValue(i), timeSeries.getTick(i).getVolume());
		}
	}

	@Test(expected = IndexOutOfBoundsException.class)
	public void testIndexGreatterThanTheIndicatorLenghtShouldThrowException() {
		volumeIndicator.getValue(10);
	}

	@Test
	public void testGetName() {
		assertEquals("VolumeIndicator", volumeIndicator.getName());
	}
}
