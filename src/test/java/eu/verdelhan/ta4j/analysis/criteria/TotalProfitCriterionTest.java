package eu.verdelhan.ta4j.analysis.criteria;

import eu.verdelhan.ta4j.AnalysisCriterion;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import java.util.ArrayList;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class TotalProfitCriterionTest {

	@Test
	public void testCalculateOnlyWithGainTrades() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 105, 110, 100, 95, 105 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertThat(profit.calculate(series, trades)).isEqualTo(1.10 * 1.05);
	}

	@Test
	public void testCalculateOnlyWithLossTrades() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.BUY), new Operation(5, OperationType.SELL)));

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertThat(profit.calculate(series, trades)).isEqualTo(0.95 * 0.7);
	}

	@Test
	public void testCalculateProfitWithTradesThatStartSelling() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.SELL), new Operation(1, OperationType.BUY)));
		trades.add(new Trade(new Operation(2, OperationType.SELL), new Operation(5, OperationType.BUY)));

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertEquals((1 / 0.95) * (1 / 0.7), profit.calculate(series, trades));
	}

	@Test
	public void testCalculateWithNoTradesShouldReturn1() {
		MockTimeSeries series = new MockTimeSeries(new double[] { 100, 95, 100, 80, 85, 70 });
		List<Trade> trades = new ArrayList<Trade>();

		AnalysisCriterion profit = new TotalProfitCriterion();
		assertThat(profit.calculate(series, trades)).isEqualTo(1d);
	}
}
