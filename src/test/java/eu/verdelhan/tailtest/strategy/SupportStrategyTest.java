package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleIndicator;

import org.junit.Before;
import org.junit.Test;

public class SupportStrategyTest {

	private SampleIndicator indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new SampleIndicator(new double[] { 96, 90, 94, 97, 95, 110 });
	}

	@Test
	public void testSupportShouldBuy() {
		Operation[] enter = new Operation[] { null, null, null, null, null, null };

		Strategy neverBuy = new FakeStrategy(enter, enter);

		Strategy support = new SupportStrategy(indicator, neverBuy, 95);

		Trade trade = new Trade();

		assertFalse(support.shouldOperate(trade, 0));
		assertTrue(support.shouldOperate(trade, 1));
		trade.operate(1);
		assertEquals(new Operation(1, OperationType.BUY), trade.getEntry());
		trade = new Trade();
		assertTrue(support.shouldOperate(trade, 2));
		trade.operate(2);
		assertEquals(new Operation(2, OperationType.BUY), trade.getEntry());
		trade = new Trade();
		assertFalse(support.shouldOperate(trade, 3));
		assertTrue(support.shouldOperate(trade, 4));
		trade.operate(4);
		assertEquals(new Operation(4, OperationType.BUY), trade.getEntry());
		assertFalse(support.shouldOperate(trade, 5));
	}
}
