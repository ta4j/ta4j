package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluator.Decision;
import eu.verdelhan.ta4j.mocks.MockDecision;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

public class AverageProfitCriterionTest {
	private MockTimeSeries series;

	private List<Trade> trades;

	@Before
	public void setUp() throws Exception {
		trades = new ArrayList<Trade>();
	}

	@Test
	public void testCalculateOnlyWithGainTrades() {
		series = new MockTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		trades.clear();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertThat(averageProfit.calculate(series, trades)).isEqualTo(1.03);
	}

	@Test
	public void testSummarize() {
		series = new MockTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		List<Decision> decisions = new LinkedList<Decision>();
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));

		List<Trade> tradesToDummy1 = new LinkedList<Trade>();
		tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		Decision dummy1 = new MockDecision(tradesToDummy1, slicer);
		decisions.add(dummy1);

		List<Trade> tradesToDummy2 = new LinkedList<Trade>();
		tradesToDummy2.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
		Decision dummy2 = new MockDecision(tradesToDummy2, slicer);
		decisions.add(dummy2);

		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertThat(averageProfit.summarize(series, decisions)).isEqualTo(1.03);
	}

	@Test
	public void testCalculateWithASimpleTrade() {
		series = new MockTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		trades.clear();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(Math.pow(110d/100, 1d/3), averageProfit.calculate(series, trades), 0.001);
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		trades.clear();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertEquals(Math.pow(95d/100 * 70d/100, 1d / 6), averageProfit.calculate(series, trades), 0.01);
	}

	@Test
	public void testCalculateWithNoTicksShouldReturn1() {
		series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		trades.clear();
		AnalysisCriterion averageProfit = new AverageProfitCriterion();
		assertThat(averageProfit.calculate(series, trades)).isEqualTo(1d);
	}

	@Test
	public void testCalculateWithOneTrade()
	{
		series = new MockTimeSeries(new double[] {100, 105});
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
		AnalysisCriterion average = new AverageProfitCriterion();
		assertEquals(Math.pow(105d / 100, 1d/2), average.calculate(series, trade));
		
	}
}
