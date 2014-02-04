package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.strategy.NotSoFastStrategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;

import org.junit.Before;
import org.junit.Test;

public class NotSoFastStrategyTest {

	private NotSoFastStrategy strategy;

	private Operation[] enter;

	private Operation[] exit;

	private MockStrategy fakeStrategy;

	@Before
	public void setUp() {
		enter = new Operation[] { new Operation(0, OperationType.BUY), null, null, null, null, null };

		exit = new Operation[] { null, new Operation(1, OperationType.SELL), null,
				new Operation(3, OperationType.SELL), new Operation(4, OperationType.SELL),
				new Operation(5, OperationType.SELL), };

		fakeStrategy = new MockStrategy(enter, exit);
	}

	@Test
	public void testWith3Ticks() {
		strategy = new NotSoFastStrategy(fakeStrategy, 3);

		assertTrue(strategy.shouldEnter(0));
		assertFalse(strategy.shouldExit(0));
		assertFalse(strategy.shouldExit(1));
		assertFalse(strategy.shouldExit(2));
		assertFalse(strategy.shouldExit(3));
		assertTrue(strategy.shouldExit(4));
		assertTrue(strategy.shouldExit(5));
	}

	@Test
	public void testWith0Ticks() {
		strategy = new NotSoFastStrategy(fakeStrategy, 0);

		assertTrue(strategy.shouldEnter(0));

		assertFalse(strategy.shouldExit(0));
		assertTrue(strategy.shouldExit(1));
		assertFalse(strategy.shouldExit(2));
		assertTrue(strategy.shouldExit(3));
		assertTrue(strategy.shouldExit(4));
		assertTrue(strategy.shouldExit(5));
	}
}
