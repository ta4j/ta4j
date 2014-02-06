package eu.verdelhan.ta4j.strategy;


import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class AndStrategyTest {

	private Operation[] enter;
	private Operation[] exit;
	private Operation[] enter2;
	private Operation[] exit2;
	private MockStrategy fakeStrategy;
	private MockStrategy fakeStrategy2;
	private AndStrategy andStrategy;

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
		
		this.andStrategy = new AndStrategy(fakeStrategy,fakeStrategy2);
	}
	
	@Test
	public void AndStrategyShouldEnterWhenThe2StrategiesEnter() {
		assertThat(andStrategy.shouldEnter(0)).isEqualTo(false);
		assertThat(andStrategy.shouldEnter(1)).isEqualTo(false);
		assertThat(andStrategy.shouldEnter(2)).isEqualTo(false);
		assertThat(andStrategy.shouldEnter(3)).isEqualTo(false);
		assertThat(andStrategy.shouldEnter(4)).isEqualTo(true);
		assertThat(andStrategy.shouldEnter(5)).isEqualTo(false);
	}
	
	@Test
	public void AndStrategyShouldExitWhenThe2StrategiesExit() {
		assertThat(andStrategy.shouldExit(0)).isEqualTo(false);
		assertThat(andStrategy.shouldExit(1)).isEqualTo(false);
		assertThat(andStrategy.shouldExit(2)).isEqualTo(false);
		assertThat(andStrategy.shouldExit(3)).isEqualTo(false);
		assertThat(andStrategy.shouldExit(4)).isEqualTo(false);
		assertThat(andStrategy.shouldExit(5)).isEqualTo(true);
	}	
}
		