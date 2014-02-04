package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.strategy.StopGainStrategy;
import eu.verdelhan.ta4j.strategy.JustBuyOnceStrategy;
import eu.verdelhan.ta4j.strategy.FakeStrategy;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class StopGainStrategyGainTest {

	private MockIndicator<Double> indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new MockIndicator<Double>(new Double[] { 100d, 98d, 103d, 115d, 107d });
	}

	@Test
	public void testStopperShouldSell() {

		Strategy justBuy = new JustBuyOnceStrategy();
		Strategy stopper = new StopGainStrategy(indicator, justBuy, 4);

		Operation buy = new Operation(0, OperationType.BUY);
		Operation sell = new Operation(3, OperationType.SELL);

		Trade trade = new Trade();
		assertTrue(stopper.shouldOperate(trade, 0));
		trade.operate(0);
		assertEquals(buy, trade.getEntry());
		assertFalse(stopper.shouldOperate(trade, 1));
		assertFalse(stopper.shouldOperate(trade, 2));

		assertTrue(stopper.shouldOperate(trade, 3));
		trade.operate(3);
		assertEquals(sell, trade.getExit());
	}

	@Test
	public void testStopperShouldSellIfStrategySays() {

		Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, new Operation(2, OperationType.BUY), null, null };
		Operation[] exit = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, new Operation(4, OperationType.SELL) };

		Strategy sell1 = new FakeStrategy(enter, exit);

		Strategy stopper = new StopGainStrategy(indicator, sell1, 5);

		Operation buy = new Operation(0, OperationType.BUY);
		Operation sell = new Operation(1, OperationType.SELL);

		Trade trade = new Trade();
		assertTrue(stopper.shouldOperate(trade, 0));
		trade.operate(0);

		assertEquals(buy, trade.getEntry());

		assertTrue(stopper.shouldOperate(trade, 1));
		trade.operate(1);

		assertEquals(sell, trade.getExit());
		
		trade = new Trade();
		buy = new Operation(2, OperationType.BUY);
		sell = new Operation(3, OperationType.SELL);
		
		assertTrue(stopper.shouldOperate(trade, 2));
		trade.operate(2);

		assertEquals(buy, trade.getEntry());

		assertTrue(stopper.shouldOperate(trade, 3));
		trade.operate(3);

		assertEquals(sell, trade.getExit());
	}

}
