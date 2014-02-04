package eu.verdelhan.ta4j.strategy;


import eu.verdelhan.ta4j.strategy.OrStrategy;
import eu.verdelhan.ta4j.strategy.FakeStrategy;
import static org.junit.Assert.assertEquals;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;

import org.junit.Before;
import org.junit.Test;

public class OrStrategyTest {

	private Operation[] enter;
	private Operation[] exit;
	private Operation[] enter2;
	private Operation[] exit2;
	private FakeStrategy fakeStrategy;
	private FakeStrategy fakeStrategy2;
	private Strategy orStrategy;

	@Before
	public void setUp() throws Exception {
		enter = new Operation[] { 
				new Operation(0, OperationType.BUY), 
				null,
				null, 
				null,
				new Operation(4, OperationType.BUY),
				null};
		
		exit = new Operation[] {
				null,
				new Operation(1, OperationType.SELL),
				null,
				null,
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
				null,
				new Operation(5, OperationType.SELL)
		};
		this.fakeStrategy = new FakeStrategy(enter,exit);
		this.fakeStrategy2 = new FakeStrategy(enter2,exit2);
		
		this.orStrategy = new OrStrategy(fakeStrategy,fakeStrategy2);
	}
	
	@Test
	public void AndStrategyShouldEnterWhenThe2StrategiesEnter() {
		assertEquals(true, orStrategy.shouldEnter(0));
		assertEquals(true, orStrategy.shouldEnter(1));
		assertEquals(false, orStrategy.shouldEnter(2));
		assertEquals(false, orStrategy.shouldEnter(3));
		assertEquals(true, orStrategy.shouldEnter(4));
		assertEquals(false, orStrategy.shouldEnter(5));
	}
	
	@Test
	public void AndStrategyShouldExitWhenThe2StrategiesExit() {
		assertEquals(false, orStrategy.shouldExit(0));
		assertEquals(true, orStrategy.shouldExit(1));
		assertEquals(true, orStrategy.shouldExit(2));
		assertEquals(false, orStrategy.shouldExit(3));
		assertEquals(false, orStrategy.shouldExit(4));
		assertEquals(true, orStrategy.shouldExit(5));
	}	
}
		