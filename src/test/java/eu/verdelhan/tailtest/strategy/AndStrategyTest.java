package net.sf.tail.strategy;


import static org.junit.Assert.assertEquals;
import net.sf.tail.Operation;
import net.sf.tail.OperationType;

import org.junit.Before;
import org.junit.Test;

public class AndStrategyTest {

	private Operation[] enter;
	private Operation[] exit;
	private Operation[] enter2;
	private Operation[] exit2;
	private FakeStrategy fakeStrategy;
	private FakeStrategy fakeStrategy2;
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
		this.fakeStrategy = new FakeStrategy(enter,exit);
		this.fakeStrategy2 = new FakeStrategy(enter2,exit2);
		
		this.andStrategy = new AndStrategy(fakeStrategy,fakeStrategy2);
	}
	
	@Test
	public void AndStrategyShouldEnterWhenThe2StrategiesEnter() {
		assertEquals(false, andStrategy.shouldEnter(0));
		assertEquals(false, andStrategy.shouldEnter(1));
		assertEquals(false, andStrategy.shouldEnter(2));
		assertEquals(false, andStrategy.shouldEnter(3));
		assertEquals(true, andStrategy.shouldEnter(4));
		assertEquals(false, andStrategy.shouldEnter(5));
	}
	
	@Test
	public void AndStrategyShouldExitWhenThe2StrategiesExit() {
		assertEquals(false, andStrategy.shouldExit(0));
		assertEquals(false, andStrategy.shouldExit(1));
		assertEquals(false, andStrategy.shouldExit(2));
		assertEquals(false, andStrategy.shouldExit(3));
		assertEquals(false, andStrategy.shouldExit(4));
		assertEquals(true, andStrategy.shouldExit(5));
	}	
}
		