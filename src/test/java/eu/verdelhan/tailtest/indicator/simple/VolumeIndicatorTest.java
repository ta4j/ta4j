package net.sf.tail.indicator.simple;

import static org.junit.Assert.assertEquals;
import net.sf.tail.TimeSeries;
import net.sf.tail.sample.SampleTimeSeries;

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
