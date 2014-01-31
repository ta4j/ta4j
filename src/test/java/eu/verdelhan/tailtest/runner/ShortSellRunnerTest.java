package eu.verdelhan.tailtest.runner;

import static org.junit.Assert.assertEquals;

import java.util.List;

import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Runner;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.mocks.MockTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;
import eu.verdelhan.tailtest.strategy.FakeStrategy;

import org.joda.time.DateTime;
import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class ShortSellRunnerTest {
	private Operation[] enter;

	private Operation[] exit;

	private Strategy strategy;

	private MockTimeSeries series;

	@Before
	public void setUp() {
		DateTime date = new DateTime();
		series = new MockTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d},
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
		Runner runner = new ShortSellRunner(slicer, strategy);
		List<Trade> trades = runner.run(0);
		assertEquals(3, trades.size());

		assertEquals(new Operation(2, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(4, OperationType.SELL), trades.get(0).getExit());

		assertEquals(new Operation(4, OperationType.SELL), trades.get(1).getEntry());
		System.out.println(trades.get(1));
		assertEquals(new Operation(6, OperationType.BUY), trades.get(1).getExit());

		assertEquals(new Operation(6, OperationType.BUY), trades.get(2).getEntry());
		assertEquals(new Operation(7, OperationType.SELL), trades.get(2).getExit());
	}

	@Test
	public void testRunWithOpenEntryBuyLeft() {
		Operation[] enter = new Operation[] { null, new Operation(1, OperationType.BUY), null, null, null, null, null, null, null };
		Operation[] exit = { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null, null };

		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		Strategy strategy = new FakeStrategy(enter, exit);
		Runner runner = new ShortSellRunner(slicer, strategy);
		List<Trade> trades = runner.run(0);
		assertEquals(1, trades.size());

		assertEquals(new Operation(1, OperationType.BUY), trades.get(0).getEntry());
		assertEquals(new Operation(3, OperationType.SELL), trades.get(0).getExit());
	}

	@Test
	public void testRunWithNoTrades() {
		Operation[] enter = new Operation[] { null, null, null, null, null, null, null, null, null };
		Operation[] exit = { null, null, null, null, null, null, null, null, null };
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
				
		Strategy strategy = new FakeStrategy(enter, exit);
		Runner runner = new ShortSellRunner(slicer, strategy);
		List<Trade> trades = runner.run(0);
		assertEquals(0, trades.size());
	}

}
