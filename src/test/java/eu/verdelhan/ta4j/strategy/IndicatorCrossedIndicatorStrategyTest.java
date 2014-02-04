package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.strategy.IndicatorCrossedIndicatorStrategy;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class IndicatorCrossedIndicatorStrategyTest {

	@Test
	public void testCrossedIndicatorShouldBuyIndex2SellIndex4() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 4d, 7d, 9d, 6d, 3d, 2d });
		Indicator<Double> second = new MockIndicator<Double>(new Double[] { 3d, 6d, 10d, 8d, 2d, 1d });

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, second);
		Trade trade = new Trade();
		assertFalse(s.shouldOperate(trade, 0));
		assertFalse(s.shouldOperate(trade, 1));

		assertTrue(s.shouldOperate(trade, 2));
		trade.operate(2);
		Operation enter = new Operation(2, OperationType.BUY);
		assertEquals(enter, trade.getEntry());

		assertFalse(s.shouldOperate(trade, 3));

		assertTrue(s.shouldOperate(trade, 4));
		trade.operate(4);
		Operation exit = new Operation(4, OperationType.SELL);
		assertEquals(exit, trade.getExit());

		assertFalse(s.shouldOperate(trade, 5));
	}

	@Test
	public void testCrossedIndicatorShouldNotEnterWhenIndicatorsAreEquals() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 2d, 3d, 4d, 5d, 6d, 7d });
		Trade trade = new Trade();

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, first);

		for (int i = 0; i < 6; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}
	}

	@Test
	public void testCrossedIndicatorShouldNotExitWhenIndicatorsBecameEquals() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 4d, 7d, 9d, 6d, 3d, 2d });
		Indicator<Double> second = new MockIndicator<Double>(new Double[] { 3d, 6d, 10d, 6d, 3d, 2d });
		Trade trade = new Trade();

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, second);

		Operation enter = new Operation(2, OperationType.BUY);
		assertTrue(s.shouldOperate(trade, 2));
		trade.operate(2);
		assertEquals(enter, trade.getEntry());

		for (int i = 3; i < 6; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}
	}

	@Test
	public void testEqualIndicatorsShouldNotExitWhenIndicatorsBecameEquals() {
		Indicator<Double> firstEqual = new MockIndicator<Double>(new Double[] { 2d, 1d, 4d, 5d, 6d, 7d });
		Indicator<Double> secondEqual = new MockIndicator<Double>(new Double[] { 1d, 3d, 4d, 5d, 6d, 7d });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		assertTrue(s.shouldOperate(trade, 1));
		trade.operate(1);
		Operation enter = trade.getEntry();

		assertNotNull(enter);

		assertEquals(enter.getType(), OperationType.BUY);

		for (int i = 2; i < 6; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}
	}

	@Test
	public void testShouldNotSellWhileIndicatorAreEquals() {
		Indicator<Double> firstEqual = new MockIndicator<Double>(new Double[] { 2d, 1d, 4d, 5d, 6d, 7d, 10d });
		Indicator<Double> secondEqual = new MockIndicator<Double>(new Double[] { 1d, 3d, 4d, 5d, 6d, 7d, 9d });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		Operation enter = new Operation(1, OperationType.BUY);

		assertTrue(s.shouldOperate(trade, 1));
		trade.operate(1);
		assertEquals(enter, trade.getEntry());

		for (int i = 2; i < 6; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}

		Operation exit = new Operation(6, OperationType.SELL);
		assertTrue(s.shouldOperate(trade, 6));
		trade.operate(6);
		assertEquals(exit, trade.getExit());
	}

	@Test
	public void testCrossShouldNotReturnNullOperations() {
		Indicator<Double> firstEqual = new MockIndicator<Double>(new Double[] { 2d, 3d, 4d, 5d, 6d, 7d, 10d });
		Indicator<Double> secondEqual = new MockIndicator<Double>(new Double[] { 1d, 3d, 4d, 5d, 6d, 7d, 9d });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		for (int i = 0; i < 7; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}
	}
}
