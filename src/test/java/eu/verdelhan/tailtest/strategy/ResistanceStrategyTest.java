package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleIndicator;

import org.junit.Before;
import org.junit.Test;

public class ResistanceStrategyTest {

	private SampleIndicator indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new SampleIndicator(new double[] { 95, 96, 95, 94, 97, 95, 110 });
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
