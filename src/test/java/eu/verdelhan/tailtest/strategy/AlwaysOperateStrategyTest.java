package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Trade;

import org.junit.Before;
import org.junit.Test;

public class AlwaysOperateStrategyTest {
	private AlwaysOperateStrategy strategy;

	@Before
	public void setUp() {
		this.strategy = new AlwaysOperateStrategy();
	}

	@Test
	public void shouldEnterBuyingIfTradeIsNew() {
		Trade trade = new Trade();
		assertTrue(strategy.shouldOperate(trade, 0));
	}

	@Test
	public void shouldExitSellingIfTradeIsOpened() {
		Trade trade = new Trade();
		trade.operate(0);

		assertTrue(strategy.shouldOperate(trade, 1));
	}

	@Test
	public void shouldNotOperateIfTradeIsClosed() {
		Trade trade = new Trade();
		trade.operate(0);
		trade.operate(1);

		assertFalse(strategy.shouldOperate(trade, 2));
	}

	@Test
	public void shouldEnterSellingIfUncoveredTradeIsNew() {
		Trade uncoveredTrade = new Trade(OperationType.SELL);
		assertTrue(strategy.shouldOperate(uncoveredTrade, 0));
	}

	@Test
	public void shouldExitBuyingIfUncoveredTradeIsOpened() {
		Trade uncoveredTrade = new Trade(OperationType.SELL);
		uncoveredTrade.operate(0);

		assertTrue(strategy.shouldOperate(uncoveredTrade, 1));
	}
}
