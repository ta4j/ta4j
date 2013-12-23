package eu.verdelhan.tailtest.graphics;

import static junit.framework.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleIndicator;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.series.DefaultTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

public class SeriesDatasetTest {

	TimeSeries series;

	List<Indicator<? extends Number>> indicators;

	List<Trade> trades;

	List<DefaultTick> ticks;

	private SampleIndicator indicator1;

	private SampleIndicator indicator2;

	@Before
	public void setUp() throws Exception {

		ticks = new LinkedList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 9), 4d));
		

		series = new DefaultTimeSeries(ticks);
		indicators = new LinkedList<Indicator<? extends Number>>();
		trades = new LinkedList<Trade>();

		indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d});
		indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d });

		indicators.add(indicator1);
		indicators.add(indicator2);

		Operation entry = new Operation(0, OperationType.BUY);
		Operation exit = new Operation(1, OperationType.SELL);
		trades.add(new Trade(entry, exit));

		entry = new Operation(2, OperationType.BUY);
		exit = new Operation(3, OperationType.SELL);
		trades.add(new Trade(entry, exit));
	}

	@Test
	public void testDefaultConstructor() {
		SeriesDataset dataset = new SeriesDataset(series, indicators, 0, 3);
		assertEquals(indicators.size(), dataset.getRowCount());
		assertEquals(series.getSize(), dataset.getColumnCount());
		assertEquals(4d, dataset.getValue(1, 1));
	}

	@Test
	public void testTradesConstructor() {
		SeriesDataset dataset = new SeriesDataset(series, indicators, 0, 3, trades);
		assertEquals(indicators.size() + trades.size(), dataset.getRowCount());
		assertEquals(series.getSize(), dataset.getColumnCount());
		assertEquals(2d, dataset.getValue(3, 1));
	}

	@Test
	public void testMoveRightWhenIndicatorDontHaveMoreDataUnmappedShouldDoNothing() {
		SeriesDataset dataset = new SeriesDataset(series, indicators, 0, 3, trades);
		dataset.moveRight(1);
		assertEquals(indicators.size() + trades.size(), dataset.getRowCount());
		assertEquals(series.getSize(), dataset.getColumnCount());
		assertEquals(2d, dataset.getValue(3, 1));
	}

	@Test
	public void testMoveLeftWhenIndicatorDontHaveMoreDataUnmappedShouldDoNothing() {
		SeriesDataset dataset = new SeriesDataset(series, indicators, 0, 3, trades);
		dataset.moveLeft(1);
		assertEquals(indicators.size() + trades.size(), dataset.getRowCount());
		assertEquals(series.getSize(), dataset.getColumnCount());
		assertEquals(2d, dataset.getValue(3, 1));
	}

	@Test
	public void testMoveRight() {
		List<DefaultTick> ticks = new ArrayList<DefaultTick>();
		ticks = this.ticks;
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 10), 4d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		List<Indicator<? extends Number>> indicators = new ArrayList<Indicator<? extends Number>>();
		SampleIndicator indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 14d });
		SampleIndicator indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 15d });
		indicators.add(indicator1);
		indicators.add(indicator2);
		SeriesDataset dataset = new SeriesDataset(series, indicators, 0, 3, trades);
	
		dataset.moveRight(1);
		assertEquals(series.getSize() - 1, dataset.getColumnCount());
		assertEquals(3d, dataset.getValue(0, 0));
	}

	@Test
	public void testMoveLeft() {
		List<DefaultTick> ticks = this.ticks;
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 10), 4d));
		SampleTimeSeries series = new SampleTimeSeries(ticks);
		List<Indicator<? extends Number>> indicators = new ArrayList<Indicator<? extends Number>>();
		SampleIndicator indicator1 = new SampleIndicator(new double[] { 2d, 3d, 4d, 5d, 14d });
		SampleIndicator indicator2 = new SampleIndicator(new double[] { 5d, 4d, 3d, 2d, 15d });
		indicators.add(indicator1);
		indicators.add(indicator2);
		SeriesDataset dataset = new SeriesDataset(series, indicators, 2, 4, trades);
				

		dataset.moveLeft(1);
		
		assertEquals(series.getSize() - 2, dataset.getColumnCount());
		assertEquals(3d, dataset.getValue(0, 0));
	}
}
