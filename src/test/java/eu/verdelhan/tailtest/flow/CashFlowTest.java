package eu.verdelhan.tailtest.flow;

import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import eu.verdelhan.tailtest.ConstrainedTimeSeries;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.TimeSeries;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleTimeSeries;
import eu.verdelhan.tailtest.tick.DefaultTick;

import org.junit.Test;

public class CashFlowTest {

	@Test
	public void testCashFlowSize() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 1d, 2d, 3d, 4d, 5d });
		CashFlow cashFlow = new CashFlow(sampleTimeSeries, new ArrayList<Trade>());
		assertEquals(5, cashFlow.getSize());
	}

	@Test
	public void testCashFlowBuyWithOnlyOneTrade() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 1d, 2d });

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(2d, cashFlow.getValue(1), 0.0001);
	}

	@Test
	public void testCashFlowWithSellAndBuyOperations() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(2, 1, 3, 5, 6, 3, 20);

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));
		trades.add(new Trade(new Operation(3, OperationType.BUY), new Operation(4, OperationType.SELL)));
		trades.add(new Trade(new Operation(5, OperationType.SELL), new Operation(6, OperationType.BUY)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(.5, cashFlow.getValue(1), 0.0001);
		assertEquals(.5, cashFlow.getValue(2), 0.0001);
		assertEquals(.5, cashFlow.getValue(3), 0.0001);
		assertEquals(.6, cashFlow.getValue(4), 0.0001);
		assertEquals(.6, cashFlow.getValue(5), 0.0001);
		assertEquals(.09, cashFlow.getValue(6), 0.0001);
	}


	@Test
	public void testCashFlowSell() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(1, 2, 4, 8, 16, 32);

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(2, OperationType.SELL), new Operation(3, OperationType.BUY)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(1d, cashFlow.getValue(1), 0.0001);
		assertEquals(1d, cashFlow.getValue(2), 0.0001);
		assertEquals(.5, cashFlow.getValue(3), 0.0001);
		assertEquals(.5, cashFlow.getValue(4), 0.0001);
		assertEquals(.5, cashFlow.getValue(5), 0.0001);
	}

	@Test
	public void testCashFlowShortSell() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(1, 2, 4, 8, 16, 32);

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(2, OperationType.SELL), new Operation(4, OperationType.BUY)));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(2d, cashFlow.getValue(1), 0.0001);
		assertEquals(4d, cashFlow.getValue(2), 0.0001);
		assertEquals(2d, cashFlow.getValue(3), 0.0001);
		assertEquals(1d, cashFlow.getValue(4), 0.0001);
		assertEquals(2d, cashFlow.getValue(5), 0.0001);
	}

	@Test
	public void testCashFlowValueWithOnlyOneTradeAndAGapBefore() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 1d, 1d, 2d });

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(1, OperationType.BUY), new Operation(2, OperationType.SELL)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(1d, cashFlow.getValue(1), 0.0001);
		assertEquals(2d, cashFlow.getValue(2), 0.0001);
	}

	@Test
	public void testCashFlowValueWithOnlyOneTradeAndAGapAfter() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 1d, 2d, 2d });

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(1, OperationType.SELL)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(3, cashFlow.getSize());
		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(2d, cashFlow.getValue(1), 0.0001);
		assertEquals(2d, cashFlow.getValue(2), 0.0001);
	}

	@Test
	public void testCashFlowValueWithTwoTradesAndLongTimeWithoutOperations() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 1d, 2d, 4d, 8d, 16d, 32d });

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(1, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(1d, cashFlow.getValue(1), 0.0001);
		assertEquals(2d, cashFlow.getValue(2), 0.0001);
		assertEquals(2d, cashFlow.getValue(3), 0.0001);
		assertEquals(2d, cashFlow.getValue(4), 0.0001);
		assertEquals(4d, cashFlow.getValue(5), 0.0001);
		assertEquals(4d, cashFlow.getValue(5), 0.0001);
	}

	@Test
	public void testCashFlowValue() {

		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 3d, 2d, 5d, 1000d, 5000d, 0.0001d, 4d, 7d,
				6d, 7d, 8d, 5d, 6d });

		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(2, OperationType.SELL)));
		trades.add(new Trade(new Operation(6, OperationType.BUY), new Operation(8, OperationType.SELL)));
		trades.add(new Trade(new Operation(9, OperationType.BUY), new Operation(11, OperationType.SELL)));

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(0), 0.0001);
		assertEquals(2d / 3, cashFlow.getValue(1), 0.0001);
		assertEquals(5d / 3, cashFlow.getValue(2), 0.0001);
		assertEquals(5d / 3, cashFlow.getValue(3), 0.0001);
		assertEquals(5d / 3, cashFlow.getValue(4), 0.0001);
		assertEquals(5d / 3, cashFlow.getValue(5), 0.0001);
		assertEquals(5d / 3, cashFlow.getValue(6), 0.0001);
		assertEquals(5d / 3 * 7d / 4, cashFlow.getValue(7), 0.0001);
		assertEquals(5d / 3 * 6d / 4, cashFlow.getValue(8), 0.0001);
		assertEquals(5d / 3 * 6d / 4, cashFlow.getValue(9), 0.0001);
		assertEquals(5d / 3 * 6d / 4 * 8d / 7, cashFlow.getValue(10), 0.0001);
		assertEquals(5d / 3 * 6d / 4 * 5d / 7, cashFlow.getValue(11), 0.0001);
		assertEquals(5d / 3 * 6d / 4 * 5d / 7, cashFlow.getValue(12), 0.0001);
	}

	@Test
	public void testCashFlowValueWithNoTrades() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d });
		List<Trade> trades = new ArrayList<Trade>();

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		assertEquals(1d, cashFlow.getValue(4));
		assertEquals(1d, cashFlow.getValue(7));
		assertEquals(1d, cashFlow.getValue(9));
	}

	@Test(expected = RuntimeException.class)
	public void testCashFlowWithIllegalArgument() {
		TimeSeries sampleTimeSeries = new SampleTimeSeries(new double[] { 3d, 2d, 5d, 4d, 7d, 6d, 7d, 8d, 5d, 6d });
		List<Trade> trades = new ArrayList<Trade>();

		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);

		cashFlow.getValue(10);
	}

	@Test
	public void testCashFlowWithConstrainedSeries() {
		SampleTimeSeries series = new SampleTimeSeries(new double[] { 5d, 6d, 3d, 7d, 8d, 6d, 10d, 15d, 6d });
		ConstrainedTimeSeries constrained = new ConstrainedTimeSeries(series, 4, 8);
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(4, OperationType.BUY), new Operation(5, OperationType.SELL)));
		trades.add(new Trade(new Operation(6, OperationType.BUY), new Operation(8, OperationType.SELL)));
		CashFlow flow = new CashFlow(constrained, trades);
		assertEquals(1d, flow.getValue(0));
		assertEquals(1d, flow.getValue(1));
		assertEquals(1d, flow.getValue(2));
		assertEquals(1d, flow.getValue(3));
		assertEquals(1d, flow.getValue(4));
		assertEquals(6d / 8, flow.getValue(5), 0.00001);
		assertEquals(6d / 8, flow.getValue(6), 0.00001);
		assertEquals(6d / 8 * 15d / 10, flow.getValue(7), 0.00001);
		assertEquals(6d / 8 * 6d / 10, flow.getValue(8), 0.00001);
	}

	@Test
	public void testReallyLongCashFlow() {
		DefaultTick tick = new DefaultTick(10);
		int size = 1000000;
		TimeSeries sampleTimeSeries = new SampleTimeSeries(Collections.nCopies(size, tick));
		List<Trade> trades = new ArrayList<Trade>();
		trades.add(new Trade(new Operation(0, OperationType.BUY), new Operation(size - 1, OperationType.SELL)));
		CashFlow cashFlow = new CashFlow(sampleTimeSeries, trades);
		assertEquals(1d, cashFlow.getValue(size - 1));
	}

}
