package eu.verdelhan.tailtest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

public class TradeTest {

	private Trade trade, uncoveredTrade, trEquals1, trEquals2, trNotEquals1, trNotEquals2;

	@Before
	public void setUp() {
		this.trade = new Trade();
		this.uncoveredTrade = new Trade(OperationType.SELL);

		trEquals1 = new Trade();
		trEquals1.operate(1);
		trEquals1.operate(2);

		trEquals2 = new Trade();
		trEquals2.operate(1);
		trEquals2.operate(2);

		trNotEquals1 = new Trade(OperationType.SELL);
		trNotEquals1.operate(1);
		trNotEquals1.operate(2);

		trNotEquals2 = new Trade(OperationType.SELL);
		trNotEquals2.operate(1);
		trNotEquals2.operate(2);
	}

	@Test
	public void whenNewShouldCreateBuyOperationWhenEntering() {
		trade.operate(0);
		assertEquals(new Operation(0, OperationType.BUY), trade.getEntry());
	}

	@Test
	public void whenNewShouldNotExit() {
		Trade trade = new Trade();
		assertFalse(trade.isOpened());
	}

	@Test
	public void whenOpenedShouldCreateSellOperationWhenExiting() {
		Trade trade = new Trade();
		trade.operate(0);
		trade.operate(1);

		assertEquals(new Operation(1, OperationType.SELL), trade.getExit());
	}

	@Test
	public void whenClosedShouldNotEnter() {
		Trade trade = new Trade();
		trade.operate(0);
		trade.operate(1);
		assertTrue(trade.isClosed());
		trade.operate(2);
		assertTrue(trade.isClosed());
	}

	@Test(expected = IllegalStateException.class)
	public void whenExitIndexIsLessThanEntryIndexShouldThrowException() {
		Trade trade = new Trade();
		trade.operate(3);
		trade.operate(1);
	}

	@Test
	public void shouldCloseTradeOnSameIndex() {
		Trade trade = new Trade();
		trade.operate(3);
		trade.operate(3);
		assertTrue(trade.isClosed());
	}

	@Test(expected = NullPointerException.class)
	public void shouldThrowNullPointerExceptionWhenOperationTyprIsNull() {
		@SuppressWarnings("unused")
		Trade t = new Trade(null);
	}

	@Test
	public void whenNewShouldCreateSellOperationWhenEnteringUncovered() {
		uncoveredTrade.operate(0);

		assertEquals(new Operation(0, OperationType.SELL), uncoveredTrade.getEntry());
	}

	@Test
	public void whenOpenedShouldCreateBuyOperationWhenExitingUncovered() {
		uncoveredTrade.operate(0);
		uncoveredTrade.operate(1);

		assertEquals(new Operation(1, OperationType.BUY), uncoveredTrade.getExit());
	}

	@Test
	public void testOverrideOperationEquals() {
		assertTrue(trEquals1.equals(trEquals2));
		assertTrue(trEquals2.equals(trEquals1));

		assertFalse(trEquals1.equals(trNotEquals1));
		assertFalse(trEquals1.equals(trNotEquals2));

		assertFalse(trEquals2.equals(trNotEquals1));
		assertFalse(trEquals2.equals(trNotEquals2));

	}

	@Test
	public void testOverrideOperationHashCode() {
		assertEquals(trEquals1.hashCode(), trEquals2.hashCode());

		assertTrue(trEquals1.hashCode() != trNotEquals1.hashCode());
		assertTrue(trEquals1.hashCode() != trNotEquals2.hashCode());

	}

	@Test
	public void testOverrideToString() {
		assertEquals(trEquals1.toString(), trEquals2.toString());

		assertFalse(trEquals1.toString().equals(trNotEquals1.toString()));
		assertFalse(trEquals1.toString().equals(trNotEquals2.toString()));

	}
}
