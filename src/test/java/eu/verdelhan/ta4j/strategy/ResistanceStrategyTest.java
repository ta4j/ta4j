package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class ResistanceStrategyTest {

	private MockIndicator<Double> indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new MockIndicator<Double>(new Double[] { 95d, 96d, 95d, 94d, 97d, 95d, 110d });
	}

	@Test
	public void testResistanceShouldSell() {
		Operation[] enter = new Operation[] { null, null, null, null, null, null, null };

		Strategy neverSell = new MockStrategy(enter, enter);

		Trade trade = new Trade();

		Strategy resistance = new ResistanceStrategy(indicator, neverSell, 96);

		trade.operate(0);
		assertTrue(resistance.shouldOperate(trade, 1));

		trade = new Trade();
		trade.operate(2);

		assertThat(resistance.shouldEnter(2)).isFalse();
		assertThat(resistance.shouldOperate(trade, 2)).isFalse();
		assertThat(resistance.shouldOperate(trade, 3)).isFalse();
		assertTrue(resistance.shouldOperate(trade, 4));

		trade = new Trade();
		trade.operate(5);

		assertThat(resistance.shouldOperate(trade, 5)).isFalse();
		assertTrue(resistance.shouldOperate(trade, 6));
	}
}
