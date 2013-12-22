package net.sf.tail.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;
import net.sf.tail.Strategy;
import net.sf.tail.Trade;

import org.junit.Before;
import org.junit.Test;

public class JustBuyOnceStrategyTest {

	private Strategy strategy;

	private Trade trade;

	@Before
	public void setUp() throws Exception {
		this.strategy = new JustBuyOnceStrategy();
		this.trade = new Trade();
	}

	@Test
	public void shouldBuyTradeOnce() {
		Operation buy = new Operation(0, OperationType.BUY);

		assertTrue(strategy.shouldOperate(trade, 0));
		trade.operate(0);
		assertEquals(buy, trade.getEntry());
		assertFalse(strategy.shouldOperate(trade, 1));
		assertFalse(strategy.shouldOperate(trade, 6));

	}

	@Test
	public void sameIndexShouldResultSameAnswer() {
		Operation buy = new Operation(0, OperationType.BUY);

		assertTrue(strategy.shouldOperate(trade, 0));
		trade.operate(0);
		assertEquals(buy, trade.getEntry());
		Trade trade2 = new Trade();
		assertFalse(strategy.shouldOperate(trade2, 0));
		trade2.operate(0);
		assertEquals(buy, trade2.getEntry());
	}

}
