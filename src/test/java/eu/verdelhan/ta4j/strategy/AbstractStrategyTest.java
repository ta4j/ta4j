package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AbstractStrategyTest {

	private Operation[] enter;
	private Operation[] exit;
	private Operation[] enter2;
	private Operation[] exit2;
	private MockStrategy fakeStrategy;
	private MockStrategy fakeStrategy2;

	@Before
	public void setUp() throws Exception {
		enter = new Operation[] { 
				new Operation(0, OperationType.BUY), 
				null,
				new Operation(2, OperationType.BUY), 
				null,
				new Operation(4, OperationType.BUY),
				null};
		
		exit = new Operation[] {
				null,
				new Operation(1, OperationType.SELL),
				null,
				new Operation(3, OperationType.SELL),
				null,
				new Operation(5, OperationType.SELL)
		};
		enter2 = new Operation[] {
				null,
				new Operation(1, OperationType.BUY), 
				null,
				null,
				new Operation(4, OperationType.BUY), 
				null};
		
		exit2 = new Operation[] {
				null,
				null,
				new Operation(2, OperationType.SELL),
				null,
				new Operation(4, OperationType.SELL),
				new Operation(5, OperationType.SELL)
		};
		this.fakeStrategy = new MockStrategy(enter,exit);
		this.fakeStrategy2 = new MockStrategy(enter2,exit2);
	}

	@Test
	public void testAnd() {
		Strategy strategy = fakeStrategy.and(fakeStrategy2);  
		
		assertThat(strategy.shouldEnter(0)).isEqualTo(false);
		assertThat(strategy.shouldEnter(1)).isEqualTo(false);
		assertThat(strategy.shouldEnter(2)).isEqualTo(false);
		assertThat(strategy.shouldEnter(3)).isEqualTo(false);
		assertThat(strategy.shouldEnter(4)).isEqualTo(true);
		assertThat(strategy.shouldEnter(5)).isEqualTo(false);
		
		assertThat(strategy.shouldExit(0)).isEqualTo(false);
		assertThat(strategy.shouldExit(1)).isEqualTo(false);
		assertThat(strategy.shouldExit(2)).isEqualTo(false);
		assertThat(strategy.shouldExit(3)).isEqualTo(false);
		assertThat(strategy.shouldExit(4)).isEqualTo(false);
		assertThat(strategy.shouldExit(5)).isEqualTo(true);
	}

	@Test
	public void testOr() {
		Strategy strategy = fakeStrategy.or(fakeStrategy2);
		
		assertThat(strategy.shouldEnter(0)).isEqualTo(true);
		assertThat(strategy.shouldEnter(1)).isEqualTo(true);
		assertThat(strategy.shouldEnter(2)).isEqualTo(true);
		assertThat(strategy.shouldEnter(3)).isEqualTo(false);
		assertThat(strategy.shouldEnter(4)).isEqualTo(true);
		assertThat(strategy.shouldEnter(5)).isEqualTo(false);
		
		assertThat(strategy.shouldExit(0)).isEqualTo(false);
		assertThat(strategy.shouldExit(1)).isEqualTo(true);
		assertThat(strategy.shouldExit(2)).isEqualTo(true);
		assertThat(strategy.shouldExit(3)).isEqualTo(true);
		assertThat(strategy.shouldExit(4)).isEqualTo(true);
		assertThat(strategy.shouldExit(5)).isEqualTo(true);
	}

}
