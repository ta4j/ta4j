package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.mocks.MockIndicator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class StopLossStrategyTest {

	private MockIndicator<Double> indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new MockIndicator<Double>(new Double[] { 100d, 100d, 96d, 95d, 94d });
	}

	@Test
	public void testStopperShouldSell() {

		Strategy justBuy = new JustBuyOnceStrategy();
		Strategy stopper = new StopLossStrategy(indicator, justBuy, 5);

		Operation buy = new Operation(0, OperationType.BUY);
		Operation sell = new Operation(4, OperationType.SELL);

		Trade trade = new Trade();
		assertTrue(stopper.shouldOperate(trade, 0));
		trade.operate(0);
		assertEquals(buy, trade.getEntry());
		assertFalse(stopper.shouldOperate(trade, 1));
		assertFalse(stopper.shouldOperate(trade, 2));

		assertTrue(stopper.shouldOperate(trade, 4));
		trade.operate(4);
		assertEquals(sell, trade.getExit());
	}

	@Test
	public void testStopperShouldSellIfStrategySays() {

		Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null };
		Operation[] exit = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, null };

		Strategy sell1 = new FakeStrategy(enter, exit);

		Strategy stopper = new StopLossStrategy(indicator, sell1, 500);

		Operation buy = new Operation(0, OperationType.BUY);
		Operation sell = new Operation(1, OperationType.SELL);

		Trade trade = new Trade();
		assertTrue(stopper.shouldOperate(trade, 0));
		trade.operate(0);

		assertEquals(buy, trade.getEntry());

		assertTrue(stopper.shouldOperate(trade, 1));
		trade.operate(1);

		assertEquals(sell, trade.getExit());
	}

}
