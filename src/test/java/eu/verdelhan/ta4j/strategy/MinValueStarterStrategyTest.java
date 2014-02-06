package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class MinValueStarterStrategyTest {

	private MockIndicator<Double> indicator;

	private int startValue;

	private Operation[] enter;

	private Operation[] exit;

	private Strategy alwaysBuy;

	private Strategy starter;

	@Before
	public void setUp() throws Exception {
		indicator = new MockIndicator<Double>(new Double[] { 90d, 92d, 96d, 95d, 92d });
		startValue = 93;
		enter = new Operation[] { new Operation(0, OperationType.BUY), new Operation(1, OperationType.BUY),
				new Operation(2, OperationType.BUY), new Operation(3, OperationType.BUY),
				new Operation(4, OperationType.BUY) };
		exit = new Operation[] { null, null, null, null, null };
		alwaysBuy = new MockStrategy(enter, exit);
		starter = new MinValueStarterStrategy(indicator, alwaysBuy, startValue);
	}

	@Test
	public void testStrategyShouldBuy() {
		Trade trade = new Trade();

		Operation buy = new Operation(2, OperationType.BUY);
		assertThat(starter.shouldOperate(trade, 2)).isTrue();
		trade.operate(2);
		assertThat(trade.getEntry()).isEqualTo(buy);

		trade = new Trade();
		buy = new Operation(3, OperationType.BUY);

		assertThat(starter.shouldOperate(trade, 3)).isTrue();
		trade.operate(3);

		assertThat(starter.shouldOperate(trade, 3)).isFalse();
		assertThat(trade.getEntry()).isEqualTo(buy);
	}

	@Test
	public void testStrategyShouldNotBuyEvenIfFakeIsSayingTo() {
		Trade trade = new Trade();
		assertThat(starter.shouldOperate(trade, 0)).isFalse();
		assertThat(starter.shouldOperate(trade, 1)).isFalse();
		assertThat(starter.shouldOperate(trade, 4)).isFalse();
	}
}
