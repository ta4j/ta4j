package eu.verdelhan.tailtest.strategy;

import eu.verdelhan.tailtest.Indicator;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Strategy;
import eu.verdelhan.tailtest.sample.SampleIndicator;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class CombinedBuyAndSellStrategyTest {

	private Operation[] enter;

	private Operation[] exit;

	private FakeStrategy buyStrategy;

	private FakeStrategy sellStrategy;

	private CombinedBuyAndSellStrategy combined;

	@Test
	public void testeShoudEnter() {

		enter = new Operation[] { new Operation(0, OperationType.BUY), null, new Operation(2, OperationType.BUY), null,
				new Operation(4, OperationType.BUY) };
		exit = new Operation[] { null, null, null, null, null };

		buyStrategy = new FakeStrategy(enter, null);
		sellStrategy = new FakeStrategy(null, exit);

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

		buyStrategy = new FakeStrategy(enter, null);
		sellStrategy = new FakeStrategy(null, exit);

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
		Indicator<Double> first = new SampleIndicator<Double>(new Double[] { 4d, 7d, 9d, 6d, 3d, 2d });
		Indicator<Double> second = new SampleIndicator<Double>(new Double[] { 3d, 6d, 10d, 8d, 2d, 1d });

		Strategy crossed = new IndicatorCrossedIndicatorStrategy(first, second);

		combined = new CombinedBuyAndSellStrategy(crossed, crossed);

		for (int index = 0; index < 6; index++) {
			assertEquals(crossed.shouldEnter(index), combined.shouldEnter(index));
			assertEquals(crossed.shouldExit(index), combined.shouldExit(index));
		}
	}
}
