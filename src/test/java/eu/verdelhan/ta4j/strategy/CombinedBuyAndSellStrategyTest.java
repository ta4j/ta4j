package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.strategy.IndicatorCrossedIndicatorStrategy;
import eu.verdelhan.ta4j.strategy.CombinedBuyAndSellStrategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CombinedBuyAndSellStrategyTest {

	private Operation[] enter;

	private Operation[] exit;

	private MockStrategy buyStrategy;

	private MockStrategy sellStrategy;

	private CombinedBuyAndSellStrategy combined;

	@Test
	public void testeShoudEnter() {

		enter = new Operation[] { new Operation(0, OperationType.BUY), null, new Operation(2, OperationType.BUY), null,
				new Operation(4, OperationType.BUY) };
		exit = new Operation[] { null, null, null, null, null };

		buyStrategy = new MockStrategy(enter, null);
		sellStrategy = new MockStrategy(null, exit);

		combined = new CombinedBuyAndSellStrategy(buyStrategy, sellStrategy);

		assertTrue(combined.shouldEnter(0));
		assertFalse(combined.shouldEnter(1));
		assertTrue(combined.shouldEnter(2));
		assertFalse(combined.shouldEnter(3));
		assertTrue(combined.shouldEnter(4));

		assertFalse(combined.shouldExit(0));
		assertFalse(combined.shouldExit(1));
		assertFalse(combined.shouldExit(2));
		assertFalse(combined.shouldExit(3));
		assertFalse(combined.shouldExit(4));

	}

	@Test
	public void testeShoudExit() {

		exit = new Operation[] { new Operation(0, OperationType.SELL), null, new Operation(2, OperationType.SELL),
				null, new Operation(4, OperationType.SELL) };

		enter = new Operation[] { null, null, null, null, null };

		buyStrategy = new MockStrategy(enter, null);
		sellStrategy = new MockStrategy(null, exit);

		combined = new CombinedBuyAndSellStrategy(buyStrategy, sellStrategy);

		assertTrue(combined.shouldExit(0));
		assertFalse(combined.shouldExit(1));
		assertTrue(combined.shouldExit(2));
		assertFalse(combined.shouldExit(3));
		assertTrue(combined.shouldExit(4));

		assertFalse(combined.shouldEnter(0));
		assertFalse(combined.shouldEnter(1));
		assertFalse(combined.shouldEnter(2));
		assertFalse(combined.shouldEnter(3));
		assertFalse(combined.shouldEnter(4));
	}

	@Test
	public void testWhenBuyStrategyAndSellStrategyAreEquals() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 4d, 7d, 9d, 6d, 3d, 2d });
		Indicator<Double> second = new MockIndicator<Double>(new Double[] { 3d, 6d, 10d, 8d, 2d, 1d });

		Strategy crossed = new IndicatorCrossedIndicatorStrategy(first, second);

		combined = new CombinedBuyAndSellStrategy(crossed, crossed);

		for (int index = 0; index < 6; index++) {
			assertEquals(crossed.shouldEnter(index), combined.shouldEnter(index));
			assertEquals(crossed.shouldExit(index), combined.shouldExit(index));
		}
	}
}
