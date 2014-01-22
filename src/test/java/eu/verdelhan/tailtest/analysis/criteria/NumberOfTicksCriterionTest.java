package eu.verdelhan.tailtest.analysis.criteria;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.analysis.evaluator.MockDecision;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;

import org.joda.time.Period;
import org.junit.Test;

public class NumberOfTicksCriterionTest {

	@Test
	public void testCalculateWithNoTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion buyAndHold = new NumberOfTicksCriterion();
		assertEquals(0d, buyAndHold.calculate(series, trades));
	}

	@Test
	public void testCalculateWithTwoTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion buyAndHold = new NumberOfTicksCriterion();
		assertEquals(6d, buyAndHold.calculate(series, trades));
	}

	@Test
	public void testSummarize() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
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
		assertEquals(6d, buyAndHold.summarize(series, decisions));
	}
}
