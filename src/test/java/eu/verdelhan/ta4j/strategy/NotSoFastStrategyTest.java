package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
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

		assertThat(strategy.shouldEnter(0)).isTrue();
		assertThat(strategy.shouldExit(0)).isFalse();
		assertThat(strategy.shouldExit(1)).isFalse();
		assertThat(strategy.shouldExit(2)).isFalse();
		assertThat(strategy.shouldExit(3)).isFalse();
		assertThat(strategy.shouldExit(4)).isTrue();
		assertThat(strategy.shouldExit(5)).isTrue();
	}

	@Test
	public void testWith0Ticks() {
		strategy = new NotSoFastStrategy(fakeStrategy, 0);

		assertThat(strategy.shouldEnter(0)).isTrue();

		assertThat(strategy.shouldExit(0)).isFalse();
		assertThat(strategy.shouldExit(1)).isTrue();
		assertThat(strategy.shouldExit(2)).isFalse();
		assertThat(strategy.shouldExit(3)).isTrue();
		assertThat(strategy.shouldExit(4)).isTrue();
		assertThat(strategy.shouldExit(5)).isTrue();
	}
}
