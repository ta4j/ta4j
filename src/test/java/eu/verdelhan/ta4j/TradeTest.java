package eu.verdelhan.ta4j;

import static org.assertj.core.api.Assertions.*;
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
		assertThat(trade.getEntry()).isEqualTo(new Operation(0, OperationType.BUY));
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

		assertThat(trade.getExit()).isEqualTo(new Operation(1, OperationType.SELL));
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

		assertThat(uncoveredTrade.getEntry()).isEqualTo(new Operation(0, OperationType.SELL));
	}

	@Test
	public void whenOpenedShouldCreateBuyOperationWhenExitingUncovered() {
		uncoveredTrade.operate(0);
		uncoveredTrade.operate(1);

		assertThat(uncoveredTrade.getExit()).isEqualTo(new Operation(1, OperationType.BUY));
	}

	@Test
	public void testOverrideToString() {
		assertThat(trEquals2.toString()).isEqualTo(trEquals1.toString());

		assertThat(trNotEquals1.toString()).isNotEqualTo(trEquals1.toString());
		assertThat(trNotEquals2.toString()).isNotEqualTo(trEquals1.toString());
	}
}
