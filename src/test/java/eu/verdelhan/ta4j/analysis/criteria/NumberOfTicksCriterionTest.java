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

public class NumberOfTicksCriterionTest {

	@Test
	public void testCalculateWithNoTrades() {
		MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion buyAndHold = new NumberOfTicksCriterion();
		assertThat(buyAndHold.calculate(series, trades)).isEqualTo(0d);
	}

	@Test
	public void testCalculateWithTwoTrades() {
		MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new NumberOfTicksCriterion();
		assertThat(buyAndHold.calculate(series, trades)).isEqualTo(6d);
	}

	@Test
	public void testSummarize() {
		MockTimeSeries series = new MockTimeSeries(100, 105, 110, 100, 95, 105);
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

		AnalysisCriterion buyAndHold = new NumberOfTicksCriterion();
		assertThat(buyAndHold.summarize(series, decisions)).isEqualTo(6d);
	}
}
