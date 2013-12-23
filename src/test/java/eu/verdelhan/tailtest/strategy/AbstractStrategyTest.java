package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertEquals;
import eu.verdelhan.tailtest.Operation;
import eu.verdelhan.tailtest.OperationType;
import eu.verdelhan.tailtest.Strategy;

import org.junit.Before;
import org.junit.Test;

public class AbstractStrategyTest {

	private Operation[] enter;
	private Operation[] exit;
	private Operation[] enter2;
	private Operation[] exit2;
	private FakeStrategy fakeStrategy;
	private FakeStrategy fakeStrategy2;

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
		this.fakeStrategy = new FakeStrategy(enter,exit);
		this.fakeStrategy2 = new FakeStrategy(enter2,exit2);
	}

	@Test
	public void testAnd() {
		Strategy strategy = fakeStrategy.and(fakeStrategy2);  
		
		assertEquals(false, strategy.shouldEnter(0));
		assertEquals(false, strategy.shouldEnter(1));
		assertEquals(false, strategy.shouldEnter(2));
		assertEquals(false, strategy.shouldEnter(3));
		assertEquals(true, strategy.shouldEnter(4));
		assertEquals(false, strategy.shouldEnter(5));
		
		assertEquals(false, strategy.shouldExit(0));
		assertEquals(false, strategy.shouldExit(1));
		assertEquals(false, strategy.shouldExit(2));
		assertEquals(false, strategy.shouldExit(3));
		assertEquals(false, strategy.shouldExit(4));
		assertEquals(true, strategy.shouldExit(5));
	}

	@Test
	public void testOr() {
		Strategy strategy = fakeStrategy.or(fakeStrategy2);
		
		assertEquals(true, strategy.shouldEnter(0));
		assertEquals(true, strategy.shouldEnter(1));
		assertEquals(true, strategy.shouldEnter(2));
		assertEquals(false, strategy.shouldEnter(3));
		assertEquals(true, strategy.shouldEnter(4));
		assertEquals(false, strategy.shouldEnter(5));
		
		assertEquals(false, strategy.shouldExit(0));
		assertEquals(true, strategy.shouldExit(1));
		assertEquals(true, strategy.shouldExit(2));
		assertEquals(true, strategy.shouldExit(3));
		assertEquals(true, strategy.shouldExit(4));
		assertEquals(true, strategy.shouldExit(5));
	}

}
