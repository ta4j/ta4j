package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleIndicator;

import org.junit.Test;

public class IndicatorCrossedIndicatorStrategyTest {

	@Test
	public void testCrossedIndicatorShouldBuyIndex2SellIndex4() {
		Indicator<Double> first = new SampleIndicator(new double[] { 4, 7, 9, 6, 3, 2 });
		Indicator<Double> second = new SampleIndicator(new double[] { 3, 6, 10, 8, 2, 1 });

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
		Indicator<Double> first = new SampleIndicator(new double[] { 2, 3, 4, 5, 6, 7 });
		Trade trade = new Trade();

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, first);

		for (int i = 0; i < 6; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}
	}

	@Test
	public void testCrossedIndicatorShouldNotExitWhenIndicatorsBecameEquals() {
		Indicator<Double> first = new SampleIndicator(new double[] { 4, 7, 9, 6, 3, 2 });
		Indicator<Double> second = new SampleIndicator(new double[] { 3, 6, 10, 6, 3, 2 });
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
		Indicator<Double> firstEqual = new SampleIndicator(new double[] { 2, 1, 4, 5, 6, 7 });
		Indicator<Double> secondEqual = new SampleIndicator(new double[] { 1, 3, 4, 5, 6, 7 });
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
		Indicator<Double> firstEqual = new SampleIndicator(new double[] { 2, 1, 4, 5, 6, 7, 10 });
		Indicator<Double> secondEqual = new SampleIndicator(new double[] { 1, 3, 4, 5, 6, 7, 9 });
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
		Indicator<Double> firstEqual = new SampleIndicator(new double[] { 2, 3, 4, 5, 6, 7, 10 });
		Indicator<Double> secondEqual = new SampleIndicator(new double[] { 1, 3, 4, 5, 6, 7, 9 });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		for (int i = 0; i < 7; i++) {
			assertFalse(s.shouldOperate(trade, i));
		}
	}
}
