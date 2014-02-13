package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.TimeSeriesSlicer;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluators.Decision;
import eu.verdelhan.ta4j.mocks.MockDecision;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import org.joda.time.Period;
import org.junit.Test;

public class BuyAndHoldCriterionTest {

	@Test
	public void testCalculateOnlyWithGainTrades() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new BuyAndHoldCriterion();
		assertThat(buyAndHold.calculate(series, trades)).isEqualTo(1.05);
	}

	@Test
	public void testSummarize() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		List<Decision> decisions = new LinkedList<Decision>();

		List<Trade> tradesToDummy1 = new LinkedList<Trade>();
		tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		Decision dummy1 = new MockDecision(tradesToDummy1, slicer);
		decisions.add(dummy1);

		List<Trade> tradesToDummy2 = new LinkedList<Trade>();
		tradesToDummy2.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));
		Decision dummy2 = new MockDecision(tradesToDummy2, slicer);
		decisions.add(dummy2);

		AnalysisCriterion buyAndHold = new BuyAndHoldCriterion();
		assertThat(buyAndHold.summarize(series, decisions)).isEqualTo(1.05);
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new BuyAndHoldCriterion();
		assertThat(buyAndHold.calculate(series, trades)).isEqualTo(0.7);
	}

	@Test
	public void testCalculateWithNoTrades() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion buyAndHold = new BuyAndHoldCriterion();
		assertThat(buyAndHold.calculate(series, trades)).isEqualTo(0.7);
	}
	
	@Test
	public void testCalculateWithOneTrade()
	{
		MockTimeSeries series = new MockTimeSeries(new double[] {100, 105 });
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
		AnalysisCriterion buyAndHold = new BuyAndHoldCriterion();
		assertThat(buyAndHold.calculate(series, trade)).isEqualTo(105d/100);	
	}
}
