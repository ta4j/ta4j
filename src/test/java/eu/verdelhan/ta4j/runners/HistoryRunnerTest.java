package eu.verdelhan.ta4j.runners;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
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
		series = new MockTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d},
					new DateTime[]{date.withYear(2000), date.withYear(2000), date.withYear(2000), date.withYear(2000), date.withYear(2001),
								   date.withYear(2001), date.withYear(2002), date.withYear(2002), date.withYear(2002)});
		
		enter = new Operation[] { null, null, new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY),
				null, null, new Operation(6, OperationType.BUY), null, null };
		exit = new Operation[] { null, null, null, null, new Operation(4, OperationType.SELL), null, null,
				new Operation(7, OperationType.SELL), new Operation(8, OperationType.SELL) };
		strategy = new MockStrategy(enter, exit);
	}

	@Test
	public void testRunMethod() {
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertThat(trades).hasSize(2);

		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));

		assertThat(trades.get(1).getEntry()).isEqualTo(new Operation(6, OperationType.BUY));
		assertThat(trades.get(1).getExit()).isEqualTo(new Operation(7, OperationType.SELL));
	}

	@Test
	public void testRunWithOpenEntryBuyLeft() {
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		Operation[] enter = new Operation[] { null, new Operation(1, OperationType.BUY), null, null, null, null, null, null, null };
		Operation[] exit = { null, null, null, new Operation(3, OperationType.SELL), null, null, null, null, null };

		Strategy strategy = new MockStrategy(enter, exit);
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertThat(trades).hasSize(1);

		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(1, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(3, OperationType.SELL));
	}

	@Test
	public void testRunWithOpenEntrySellLeft() {
		Operation[] enter = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null, null, null, null, null };
		Operation[] exit = { null, null, null, new Operation(3, OperationType.BUY), null, null, null, null, null };

		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		Strategy strategy = new MockStrategy(enter, exit);
		HistoryRunner historyRunner = new HistoryRunner(OperationType.SELL, slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertThat(trades).hasSize(1);

		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(1, OperationType.SELL));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(3, OperationType.BUY));
	}


	@Test(expected = NullPointerException.class)
	public void testNullTypeShouldThrowException() {
		HistoryRunner runner = new HistoryRunner(null, null);
	}

	@Test
	public void testRunSplitted() {
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		assertThat(trades).hasSize(1);
		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));
		
		trades = historyRunner.run(1);

		assertThat(trades).isEmpty();
		

		trades = historyRunner.run(2);

		assertThat(trades).hasSize(1);
		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(6, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(7, OperationType.SELL));
		
	}
	
	@Test
	public void testSplitted(){
		DateTime date = new DateTime();
		TimeSeries series = new MockTimeSeries(new double[]{1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d, 9d, 10d},
					new DateTime[]{date.withYear(2000), date.withYear(2000), date.withYear(2001), date.withYear(2001), date.withYear(2002),
								   date.withYear(2002), date.withYear(2002), date.withYear(2003), date.withYear(2004), date.withYear(2005)});
		
		Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, new Operation(3, OperationType.BUY),
				null, new Operation(5, OperationType.BUY), null, new Operation(7, OperationType.BUY), null, null };
		Operation[] exit = new Operation[] { null, null, new Operation(2, OperationType.SELL), null, new Operation(4, OperationType.SELL), null, new Operation(6, OperationType.SELL),
				null, null, new Operation(9, OperationType.SELL) };
		Strategy strategy = new MockStrategy(enter, exit);
		
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(1));
		HistoryRunner historyRunner = new HistoryRunner(slicer, strategy);
		List<Trade> trades = historyRunner.run(0);
		
		assertThat(trades).hasSize(1);
		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(0, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(2, OperationType.SELL));
		
		trades = historyRunner.run(1);
		
		assertThat(trades).hasSize(1);
		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(3, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(4, OperationType.SELL));
		
		trades = historyRunner.run(2);
		
		assertThat(trades).hasSize(1);
		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(5, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(6, OperationType.SELL));
		
		
		trades = historyRunner.run(3);
		
		assertThat(trades).hasSize(1);
		assertThat(trades.get(0).getEntry()).isEqualTo(new Operation(7, OperationType.BUY));
		assertThat(trades.get(0).getExit()).isEqualTo(new Operation(9, OperationType.SELL));
		
		trades = historyRunner.run(4);
		assertThat(trades).isEmpty();
		
		trades = historyRunner.run(5);
		assertThat(trades).isEmpty();
		
	}
}
