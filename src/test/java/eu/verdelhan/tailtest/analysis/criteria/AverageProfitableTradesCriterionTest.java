package net.sf.tail.analysis.criteria;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import net.sf.tail.AnalysisCriterion;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.TimeSeries;
import net.sf.tail.TimeSeriesSlicer;
import net.sf.tail.Trade;
import net.sf.tail.analysis.evaluator.Decision;
import net.sf.tail.analysis.evaluator.DummyDecision;
import net.sf.tail.sample.SampleTimeSeries;
import net.sf.tail.series.RegularSlicer;

import org.joda.time.Period;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
public class AverageProfitableTradesCriterionTest {

	@Test
	public void testCalculate()
	{
		TimeSeries series = new SampleTimeSeries(new double[]{100d, 95d, 102d, 105d, 97d, 113d});
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.BUY)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY)));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.BUY)));
		
		AverageProfitableTradesCriterion average = new AverageProfitableTradesCriterion();
		
		assertEquals(2d/3, average.calculate(series, trades));
	}
	@Test
	public void testCalculateWithOneTrade()
	{
		TimeSeries series = new SampleTimeSeries(new double[]{100d, 95d, 102d, 105d, 97d, 113d});
		
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.BUY));
			
		AverageProfitableTradesCriterion average = new AverageProfitableTradesCriterion();
		
		assertEquals(0d, average.calculate(series, trade));
		
		trade = new Trade(new Operation(1, OperationType.BUY), new Operation(2, OperationType.BUY));
		
		assertEquals(1d, average.calculate(series, trade));
	}
	
	@Test
	public void testSummarize() {
		
		TimeSeries series = new SampleTimeSeries(100d, 105d, 110d, 100d, 95d, 105d);
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		List<Decision> decisions = new LinkedList<Decision>();

		List<Trade> trades = new LinkedList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
		Decision dummy1 = new DummyDecision(trades, slicer);
		decisions.add(dummy1);

		List<Trade> trades2 = new LinkedList<Trade>();
		trades2.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		Decision dummy2 = new DummyDecision(trades2, slicer);
		decisions.add(dummy2);

		AnalysisCriterion averateProfitable = new AverageProfitableTradesCriterion();
		assertEquals(2d/3, averateProfitable.summarize(series, decisions), 0.01);
	}
	@Test
	public void testEquals()
	{
		AverageProfitableTradesCriterion criterion = new AverageProfitableTradesCriterion();
		assertTrue(criterion.equals(criterion));
		assertTrue(criterion.equals(new AverageProfitableTradesCriterion()));
		assertFalse(criterion.equals(new TotalProfitCriterion()));
		assertFalse(criterion.equals(5d));
		assertFalse(criterion.equals(null));
	}
	
}
