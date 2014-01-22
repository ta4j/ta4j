package eu.verdelhan.tailtest.analysis.criteria;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.List;

import eu.verdelhan.tailtest.AnalysisCriterion;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;

import org.junit.Test;

public class TotalProfitCriterionTest {

	@Test
	public void testCalculateOnlyWithGainTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertEquals(1.10 * 1.05, profit.calculate(series, trades));
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertEquals(0.95 * 0.7, profit.calculate(series, trades));
	}

	@Test
	public void testCalculateProfitWithTradesThatStartSelling() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.SELL), new Operation(1, OperationType.BUY)));
		trades.add(new Trade(new Operation(2, OperationType.SELL), new Operation(5, OperationType.BUY)));

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertEquals((1 / 0.95) * (1 / 0.7), profit.calculate(series, trades));
	}

	@Test
	public void testCalculateWithNoTradesShouldReturn1() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertEquals(1d, profit.calculate(series, trades));
	}
}
