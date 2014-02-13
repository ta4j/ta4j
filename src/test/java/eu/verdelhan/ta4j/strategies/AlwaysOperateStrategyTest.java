package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.AlwaysOperateStrategy;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Trade;
import static org.assertj.core.api.Assertions.*;
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
		assertThat(strategy.shouldOperate(trade, 0)).isTrue();
	}

	@Test
	public void shouldExitSellingIfTradeIsOpened() {
		Trade trade = new Trade();
		trade.operate(0);

		assertThat(strategy.shouldOperate(trade, 1)).isTrue();
	}

	@Test
	public void shouldNotOperateIfTradeIsClosed() {
		Trade trade = new Trade();
		trade.operate(0);
		trade.operate(1);

		assertThat(strategy.shouldOperate(trade, 2)).isFalse();
	}

	@Test
	public void shouldEnterSellingIfUncoveredTradeIsNew() {
		Trade uncoveredTrade = new Trade(OperationType.SELL);
		assertThat(strategy.shouldOperate(uncoveredTrade, 0)).isTrue();
	}

	@Test
	public void shouldExitBuyingIfUncoveredTradeIsOpened() {
		Trade uncoveredTrade = new Trade(OperationType.SELL);
		uncoveredTrade.operate(0);

		assertThat(strategy.shouldOperate(uncoveredTrade, 1)).isTrue();
	}
}
