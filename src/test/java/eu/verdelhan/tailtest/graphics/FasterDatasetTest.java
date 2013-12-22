package net.sf.tail.graphics;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.junit.Test;

public class FasterDatasetTest {


	@Test
	public void testFasterDataset()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 7d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		FasterDataset dataset = new FasterDataset(series, true);
		assertEquals(6, dataset.getColumnCount());
	}
	
	@Test
	public void testFasterDatasetWithoutDoFast()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 7d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		FasterDataset dataset = new FasterDataset(series);
		assertEquals(8, dataset.getColumnCount());
	}
	
	@Test
	public void testFasterDatasetOnlyWithGains()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 7d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 8d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		FasterDataset dataset = new FasterDataset(series, true);
		assertEquals(4, dataset.getColumnCount());
	}
	@Test
	public void testFasterDatasetNotSoFast()
	{
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 5, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 7, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 8, 9), 4d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 9, 10), 5d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 10, 11), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 11, 12), 6d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 12, 13), 7d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		FasterDataset dataset = new FasterDataset(series, false);
		assertEquals(8, dataset.getColumnCount());
	}
	
}
