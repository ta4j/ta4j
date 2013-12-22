package net.sf.tail.graphics;

import static junit.framework.Assert.assertEquals;

import java.util.LinkedList;
import java.util.List;

import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.TimeSeries;
import net.sf.tail.Trade;
import net.sf.tail.flow.CashFlow;
import net.sf.tail.indicator.simple.ClosePriceIndicator;
import net.sf.tail.series.DefaultTimeSeries;
import net.sf.tail.tick.DefaultTick;

import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;

public class StockAndCashFlowDatasetTest {

	TimeSeries series;

	ClosePriceIndicator close;

	CashFlow cashFlow;

	List<Trade> trades;

	List<DefaultTick> ticks;

	@Before
	public void setUp() throws Exception {

		ticks = new LinkedList<DefaultTick>();
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 6), 1d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 7), 2d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 8), 3d));
		ticks.add(new DefaultTick(new DateTime().withDate(2007, 6, 9), 4d));

		series = new DefaultTimeSeries(ticks);
		trades = new LinkedList<Trade>();

		Operation entry = new Operation(0, OperationType.BUY);
		Operation exit = new Operation(1, OperationType.SELL);
		trades.add(new Trade(entry, exit));

		entry = new Operation(2, OperationType.BUY);
		exit = new Operation(3, OperationType.SELL);
		trades.add(new Trade(entry, exit));

		close = new ClosePriceIndicator(series);
		cashFlow = new CashFlow(series, trades);
	}

	@Test
	public void testDefaultConstructor() {
		StockAndCashFlowDataset dataset = new StockAndCashFlowDataset(series, close, cashFlow);
		assertEquals(2, dataset.getRowCount());
		assertEquals(series.getSize(), dataset.getColumnCount());
		assertEquals(1d, dataset.getValue(1, 0));
		assertEquals(8d / 3, dataset.getValue(1, 3));
		assertEquals(1d, dataset.getValue(0, 0));
		assertEquals(4d, dataset.getValue(0, 3));
	}

	@Test
	public void testConstructorWithSizes() {
		StockAndCashFlowDataset dataset = new StockAndCashFlowDataset(series, close, cashFlow, series.getBegin(),
				series.getEnd());
		assertEquals(2, dataset.getRowCount());
		assertEquals(series.getSize(), dataset.getColumnCount());
		assertEquals(1d, dataset.getValue(1, 0));
		assertEquals(8d / 3, dataset.getValue(1, 3));
		assertEquals(1d, dataset.getValue(0, 0));
		assertEquals(4d, dataset.getValue(0, 3));
	}
}
