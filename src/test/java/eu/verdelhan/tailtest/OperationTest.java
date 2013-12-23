package eu.verdelhan.tailtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import junit.framework.JUnit4TestAdapter;

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
	public void testOverrideOperationEquals() {
		assertTrue(opEquals1.equals(opEquals2));
		assertTrue(opEquals2.equals(opEquals1));

		assertFalse(opEquals1.equals(opNotEquals1));
		assertFalse(opEquals1.equals(opNotEquals2));

		assertFalse(opEquals2.equals(opNotEquals1));
		assertFalse(opEquals2.equals(opNotEquals2));

	}

	@Test
	public void testOverrideOperationHashCode() {
		assertEquals(opEquals1.hashCode(), opEquals2.hashCode());

		assertTrue(opEquals1.hashCode() != opNotEquals1.hashCode());
		assertTrue(opEquals1.hashCode() != opNotEquals2.hashCode());

	}

	@Test
	public void testOverrideToString() {
		assertEquals(opEquals1.toString(), opEquals2.toString());

		assertFalse(opEquals1.toString().equals(opNotEquals1.toString()));
		assertFalse(opEquals1.toString().equals(opNotEquals2.toString()));

	}

	public static junit.framework.Test suite() {
		return new JUnit4TestAdapter(OperationTest.class);
	}

}
