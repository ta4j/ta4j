package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.strategy.ResistanceStrategy;
import eu.verdelhan.ta4j.strategy.FakeStrategy;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.junit.Assert.assertFalse;
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

		Strategy neverSell = new FakeStrategy(enter, enter);

		Trade trade = new Trade();

		Strategy resistance = new ResistanceStrategy(indicator, neverSell, 96);

		trade.operate(0);
		assertTrue(resistance.shouldOperate(trade, 1));

		trade = new Trade();
		trade.operate(2);

		assertFalse(resistance.shouldEnter(2));
		assertFalse(resistance.shouldOperate(trade, 2));
		assertFalse(resistance.shouldOperate(trade, 3));
		assertTrue(resistance.shouldOperate(trade, 4));

		trade = new Trade();
		trade.operate(5);

		assertFalse(resistance.shouldOperate(trade, 5));
		assertTrue(resistance.shouldOperate(trade, 6));
	}
}
