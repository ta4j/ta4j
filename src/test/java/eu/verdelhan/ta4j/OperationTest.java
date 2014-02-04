package eu.verdelhan.ta4j;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import org.junit.Before;
import org.junit.Test;

public class OperationTest {

	Operation opEquals1, opEquals2, opNotEquals1, opNotEquals2;

	@Before
	public void setUp() {
		opEquals1 = new Operation(1, OperationType.BUY);
		opEquals2 = new Operation(1, OperationType.BUY);

		opNotEquals1 = new Operation(1, OperationType.SELL);
		opNotEquals2 = new Operation(2, OperationType.BUY);
	}

	@Test
	public void testOverrideToString() {
		assertEquals(opEquals1.toString(), opEquals2.toString());

		assertFalse(opEquals1.toString().equals(opNotEquals1.toString()));
		assertFalse(opEquals1.toString().equals(opNotEquals2.toString()));

	}
}
