package net.sf.tail.runner;

import static org.junit.Assert.assertEquals;

import java.util.List;

import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Strategy;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;
import net.sf.tail.strategy.FakeStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class HistoryRunnerTest {

	private Operation[] enter;

	private Operation[] exit;

	private Strategy strategy;
	
	private TimeSeries series;

	@Before
	public void setUp() {
		DateTime date = new DateTime();
		series = new SampleTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d},
					new DateTime[]{date.withYear(2000), date.withYear(2000), date.withYear(2000), date.withYear(2000), date.withYear(2001),
								   date.withYear(2001), date.withYear(2002), date.withYear(2002), date.withYear(2002)});
		
		enter = new Operation[] { null, null, new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY),
				null, null, new Operation(6, OperationType.BUY), null, null };
		exit = new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null,
				new Operation(7, OperationType.SELL), new Operation(8, OperationType.SELL) };
		strategy = new FakeStrategy(enter, exit);
	}

	@Test
	public void testRunMethod() {
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertEquals(2, trades.size());

		assertEquals(new Operation(2, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());

		assertEquals(new Operation(6, OperationType.BUY), trades.get(1).getEntry());
		assertEquals(new Operation(7, OperationType.SELL), trades.get(1).getExit());
	}

	@Test
	public void testRunWithOpenEntryBuyLeft() {
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		Operation[] enter = new Operation[] { null, new Operation(1, OperationType.BUY), null, null, null, null, null, null, null };
		Operation[] exit = { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null, null };

		Strategy strategy = new FakeStrategy(enter, exit);
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertEquals(1, trades.size());

		assertEquals(new Operation(1, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(3, OperationType.SELL), trades.get(0).getExit());
	}

	@Test
	public void testRunWithOpenEntrySellLeft() {
		Operation[] enter = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null, null, null, null, null };
		Operation[] exit = { null, null, null, new Operation(3, OperationType.BUY), null, null, null, null, null };

		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		Strategy strategy = new FakeStrategy(enter, exit);
		HistoryRunner historyRunner = new HistoryRunner(OperationType.SELL, slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertEquals(1, trades.size());

		assertEquals(new Operation(1, OperationType.SELL), trades.get(0).getEntry());
		assertEquals(new Operation(3, OperationType.BUY), trades.get(0).getExit());
	}


	@Test(expected = NullPointerException.class)
	public void testNullTypeShouldThrowException() {
		@SuppressWarnings("unused")
		HistoryRunner runner = new HistoryRunner(null, null);
	}

	@Test
	public void testRunSplitted() {
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertEquals(1, trades.size());
		assertEquals(new Operation(2, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());
		
		trades = historyRunner.run(1);

		assertEquals(0, trades.size());
		

		trades = historyRunner.run(2);

		assertEquals(1, trades.size());
		assertEquals(new Operation(6, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(7, OperationType.SELL), trades.get(0).getExit());
		
	}
	
	@Test
	public void testSplitted(){
		DateTime date = new DateTime();
		TimeSeries series = new SampleTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d},
					new DateTime[]{date.withYear(2000), date.withYear(2000), date.withYear(2001), date.withYear(2001), date.withYear(2002),
								   date.withYear(2002), date.withYear(2002), date.withYear(2003), date.withYear(2004), date.withYear(2005)});
		
		Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, new Operation(3, OperationType.BUY),
				null, new Operation(5, OperationType.BUY), null, new Operation(7, OperationType.BUY), null, null };
		Operation[] exit = new Operation[] { null, null, new Operation(2, OperationType.SELL), null, new Operation(4, OperationType.SELL), null, new Operation(6, OperationType.SELL),
				null, null, new Operation(9, OperationType.SELL) };
		Strategy strategy = new FakeStrategy(enter, exit);
		
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		
		assertEquals(1, trades.size());
		assertEquals(new Operation(0, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(2, OperationType.SELL), trades.get(0).getExit());
		
		trades = historyRunner.run(1);
		
		assertEquals(1, trades.size());
		assertEquals(new Operation(3, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());
		
		trades = historyRunner.run(2);
		
		assertEquals(1, trades.size());
		assertEquals(new Operation(5, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(6, OperationType.SELL), trades.get(0).getExit());
		
		
		trades = historyRunner.run(3);
		
		assertEquals(1, trades.size());
		assertEquals(new Operation(7, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(9, OperationType.SELL), trades.get(0).getExit());
		
		trades = historyRunner.run(4);
		assertEquals(0, trades.size());
		
		trades = historyRunner.run(5);
		assertEquals(0, trades.size());
		
	}
}
