package eu.verdelhan.ta4j;

import static org.assertj.core.api.Assertions.*;
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
		assertThat(opEquals2.toString()).isEqualTo(opEquals1.toString());

		assertThat(opNotEquals1.toString()).isNotEqualTo(opEquals1.toString());
		assertThat(opNotEquals2.toString()).isNotEqualTo(opEquals1.toString());
	}
}
