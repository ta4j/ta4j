package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.mocks.MockIndicator;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class DistanceBetweenIndicatorsStrategyTest {
	private MockIndicator<Double> upper;

	private MockIndicator<Double> lower;

	private AbstractStrategy distanceEnter;

	@Before
	public void setUp() {
		upper = new MockIndicator<Double>(new Double[] { 30d, 32d, 33d, 32d, 35d, 33d, 32d });
		lower = new MockIndicator<Double>(new Double[] { 10d, 10d, 10d, 12d, 14d, 15d, 15d });
		distanceEnter = new DistanceBetweenIndicatorsStrategy(upper, lower, 20, 0.1);
	}

	@Test
	public void testStrategyIsBuyingCorreclty() {
		Trade trade = new Trade();

		assertFalse(distanceEnter.shouldOperate(trade, 0));
		assertTrue(distanceEnter.shouldOperate(trade, 1));
		trade = new Trade();
		assertTrue(distanceEnter.shouldOperate(trade, 2));
		assertFalse(distanceEnter.shouldOperate(trade, 3));
		assertFalse(distanceEnter.shouldOperate(trade, 4));
	}

	@Test
	public void testStrategyIsSellingCorrectly() {
		Trade trade = new Trade();
		trade.operate(2);

		assertFalse(distanceEnter.shouldOperate(trade, 0));
		assertTrue(distanceEnter.shouldOperate(trade, 5));

		trade = new Trade();
		trade.operate(2);

		assertTrue(distanceEnter.shouldOperate(trade, 6));
		assertFalse(distanceEnter.shouldOperate(trade, 3));
		assertFalse(distanceEnter.shouldOperate(trade, 4));

	}
}
