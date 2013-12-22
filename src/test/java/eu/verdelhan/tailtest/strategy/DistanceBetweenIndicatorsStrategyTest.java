package net.sf.tail.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.sf.tail.Trade;
import net.sf.tail.sample.SampleIndicator;

import org.junit.Before;
import org.junit.Test;

public class DistanceBetweenIndicatorsStrategyTest {
	private SampleIndicator upper;

	private SampleIndicator lower;

	private AbstractStrategy distanceEnter;

	@Before
	public void setUp() {
		upper = new SampleIndicator(new double[] { 30, 32, 33, 32, 35, 33, 32 });
		lower = new SampleIndicator(new double[] { 10, 10, 10, 12, 14, 15, 15 });
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
