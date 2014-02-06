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

public class SupportStrategyTest {

	private MockIndicator<Double> indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new MockIndicator<Double>(new Double[] { 96d, 90d, 94d, 97d, 95d, 110d });
	}

	@Test
	public void testSupportShouldBuy() {
		Operation[] enter = new Operation[] { null, null, null, null, null, null };

		Strategy neverBuy = new MockStrategy(enter, enter);

		Strategy support = new SupportStrategy(indicator, neverBuy, 95);

		Trade trade = new Trade();

		assertThat(support.shouldOperate(trade, 0)).isFalse();
		assertThat(support.shouldOperate(trade, 1)).isTrue();
		trade.operate(1);
		assertThat(trade.getEntry()).isEqualTo(new Operation(1, OperationType.BUY));
		trade = new Trade();
		assertThat(support.shouldOperate(trade, 2)).isTrue();
		trade.operate(2);
		assertThat(trade.getEntry()).isEqualTo(new Operation(2, OperationType.BUY));
		trade = new Trade();
		assertThat(support.shouldOperate(trade, 3)).isFalse();
		assertThat(support.shouldOperate(trade, 4)).isTrue();
		trade.operate(4);
		assertThat(trade.getEntry()).isEqualTo(new Operation(4, OperationType.BUY));
		assertThat(support.shouldOperate(trade, 5)).isFalse();
	}
}
