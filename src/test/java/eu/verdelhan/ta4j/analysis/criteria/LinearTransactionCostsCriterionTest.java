package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.analysis.criteria.LinearTransactionCostsCriterion;
import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.analysis.evaluator.Decision;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import eu.verdelhan.ta4j.series.RegularSlicer;
import java.util.ArrayList;
import java.util.List;
import org.joda.time.DateTime;
import org.joda.time.Period;
import static org.junit.Assert.assertEquals;
import org.junit.Test;


public class LinearTransactionCostsCriterionTest {
	
	@Test
	public void testCalculate() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		AnalysisCriterion transactionCost = new LinearTransactionCostsCriterion(0, 40);
		
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		
		assertEquals(40d, transactionCost.calculate(series, trades));
		
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));

		
		assertEquals(80d, transactionCost.calculate(series, trades));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		assertEquals(120d, transactionCost.calculate(series, trades));
	}

	@Test
	public void testCalculateWithOneTrade() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));
		AnalysisCriterion transactionCost = new LinearTransactionCostsCriterion(0, 40);
		assertEquals(40d, transactionCost.calculate(series, trade));
	}
	@Test
	public void testSummarize() {
		DateTime date = new DateTime();
		
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 }, new DateTime[]{date, date, date, date, date, date});
		List<Trade> trades = new ArrayList<Trade>();
		List<Decision> decisions = new ArrayList<Decision>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)), 0,null, trades, null));
		
		trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(3, OperationType.SELL)));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)),0, null, trades, null));
		decisions.add(new Decision(null, new RegularSlicer(series, new Period().withYears(2000)),0, null, trades, null));
		
		AnalysisCriterion transactionCosts = new LinearTransactionCostsCriterion(0, 40);
		assertEquals(200d, transactionCosts.summarize(series, decisions));	
	}
}
