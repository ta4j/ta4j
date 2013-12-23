package eu.verdelhan.tailtest.analysis.criteria;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeriesSlicer;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.analysis.evaluator.Decision;
import eu.verdelhan.tailtest.analysis.evaluator.DummyDecision;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.series.RegularSlicer;

import org.joda.time.Period;
import org.junit.Before;
import org.junit.Test;

public class BrazilianRewardRiskRatioCriterionTest {

	private BrazilianRewardRiskRatioCriterion rrc;

	@Before
	public void setUp() {
		this.rrc = new BrazilianRewardRiskRatioCriterion();
	}

	@Test
	public void testRewardRiskRatioCriterion() {
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(4, OperationType.SELL)));
		trades.add(new Trade(new Operation(5, OperationType.BUY), new Operation(7, OperationType.SELL)));

		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 95, 100, 90, 95, 80, 120 });

		double totalProfit = ((105d * 0.99965d)/ (100d * 1.00035d)) * ((90d * 0.99965d)/ (95d * 1.00035d)) * ((120d * 0.99965d) / (95 * 1.00035d));
		double peak = (105d / 100) * (100d / 95);
		double low = (105d / 100) * (90d / 95) * (80d / 95);
			
		assertEquals(totalProfit / ((peak - low) / peak),
				rrc.calculate(series, trades), 0.001);
	}

	@Test
	public void testSummarize() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 95, 100, 90, 95, 80, 120 });
		TimeSeriesSlicer slicer = new RegularSlicer(series, new Period().withYears(2000));
		List<Decision> decisions = new LinkedList<Decision>();

		List<Trade> tradesToDummy1 = new LinkedList<Trade>();
		tradesToDummy1.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		Decision dummy1 = new DummyDecision(tradesToDummy1, slicer);
		decisions.add(dummy1);

		List<Trade> tradesToDummy2 = new LinkedList<Trade>();
		tradesToDummy2.add(new Trade(new Operation(2, OperationType.BUY), new Operation(4, OperationType.SELL)));
		Decision dummy2 = new DummyDecision(tradesToDummy2, slicer);
		decisions.add(dummy2);

		List<Trade> tradesToDummy3 = new LinkedList<Trade>();
		tradesToDummy3.add(new Trade(new Operation(5, OperationType.BUY), new Operation(7, OperationType.SELL)));
		Decision dummy3 = new DummyDecision(tradesToDummy3, slicer);
		decisions.add(dummy3);

		double totalProfit = ((105d * 0.99965d)/ (100d * 1.00035d)) * ((90d * 0.99965d)/ (95d * 1.00035d)) * ((120d * 0.99965d) / (95 * 1.00035d));
		double peak = (105d / 100) * (100d / 95);
		double low = (105d / 100) * (90d / 95) * (80d / 95);
		
		assertEquals(totalProfit / ((peak - low) / peak),
				rrc.summarize(series, decisions), 0.001);
	}

	@Test
	public void testRewardRiskRatioCriterionOnlyWithGain() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 2, 3, 6, 8, 20, 3 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		assertTrue(Double.isInfinite(rrc.calculate(series, trades)));

	}

	@Test
	public void testRewardRiskRatioCriterionWithNoTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 1, 2, 3, 6, 8, 20, 3 });
		List<Trade> trades = new ArrayList<Trade>();

		assertTrue(Double.isInfinite(rrc.calculate(series, trades)));

	}
	
	@Test
	public void testWithOneTrade() {
		Trade trade = new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL));

		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 95, 100, 90, 95, 80, 120 });

		assertEquals(((95d* 0.99965d) /(100 * 1.00035d)) / ((1d - 0.95d)), rrc.calculate(series, trade));
	}
	
	@Test
	public void testEquals()
	{
		BrazilianRewardRiskRatioCriterion criterion = new BrazilianRewardRiskRatioCriterion();
		assertTrue(criterion.equals(criterion));
		assertTrue(criterion.equals(new BrazilianRewardRiskRatioCriterion()));
		assertFalse(criterion.equals(new TotalProfitCriterion()));
		assertFalse(criterion.equals(5d));
		assertFalse(criterion.equals(null));
	}

}
