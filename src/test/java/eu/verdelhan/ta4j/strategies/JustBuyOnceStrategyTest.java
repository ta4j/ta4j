package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.JustBuyOnceStrategy;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import static org.assertj.core.api.Assertions.*;
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

		assertThat(strategy.shouldOperate(trade, 0)).isTrue();
		trade.operate(0);
		assertThat(trade.getEntry()).isEqualTo(buy);
		assertThat(strategy.shouldOperate(trade, 1)).isFalse();
		assertThat(strategy.shouldOperate(trade, 6)).isFalse();

	}

	@Test
	public void sameIndexShouldResultSameAnswer() {
		Operation buy = new Operation(0, OperationType.BUY);

		assertThat(strategy.shouldOperate(trade, 0)).isTrue();
		trade.operate(0);
		assertThat(trade.getEntry()).isEqualTo(buy);
		Trade trade2 = new Trade();
		assertThat(strategy.shouldOperate(trade2, 0)).isFalse();
		trade2.operate(0);
		assertThat(trade2.getEntry()).isEqualTo(buy);
	}

}
