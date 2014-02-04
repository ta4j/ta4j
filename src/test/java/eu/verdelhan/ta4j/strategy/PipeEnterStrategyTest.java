package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.strategy.PipeEnterStrategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;

public class PipeEnterStrategyTest {
	private MockIndicator<Double> upper;

	private MockIndicator<Double> lower;

	@Before
	public void setUp() {
		upper = new MockIndicator<Double>(new Double[] { 30d, 32d, 33d, 32d, 35d, 33d, 32d, 33d, 31d, 30d, 31d, 32d, 32d, 34d, 35d });
		lower = new MockIndicator<Double>(new Double[] { 10d, 12d, 13d, 12d, 15d, 13d, 12d, 13d, 11d, 10d, 11d, 12d, 12d, 14d, 15d });
	}

	@Test
	public void testFirstSellLastBuy() {
		Trade trade = new Trade();
		MockIndicator<Double> value = new MockIndicator<Double>(new Double[] { 25d, 27d, 28d, 27d, 30d, 33d, 35d, 37d, 35d, 29d, 11d, 10d, 15d,
				30d, 31d });

		PipeEnterStrategy pipeEnter = new PipeEnterStrategy(upper, lower, value);

		assertFalse(pipeEnter.shouldOperate(trade, 1));
		assertFalse(pipeEnter.shouldOperate(trade, 8));
		assertFalse(pipeEnter.shouldOperate(trade, 10));
		trade.operate(8);
		assertTrue(pipeEnter.shouldOperate(trade, 9));
		trade = new Trade();
		assertFalse(pipeEnter.shouldOperate(trade, 11));
		assertTrue(pipeEnter.shouldOperate(trade, 12));
	}

	@Test
	public void testFirstBuyLastSell() {
		Trade trade = new Trade();
		MockIndicator<Double> value = new MockIndicator<Double>(new Double[] { 8d, 15d, 16d, 33d, 40d, 45d, 47d, 40d, 32d, 25d, 15d, 16d, 11d,
				10d, 12d });

		PipeEnterStrategy pipeEnter = new PipeEnterStrategy(upper, lower, value);

		assertFalse(pipeEnter.shouldOperate(trade, 0));
		assertTrue(pipeEnter.shouldOperate(trade, 1));
		trade.operate(1);
		assertFalse(pipeEnter.shouldOperate(trade, 8));
		assertFalse(pipeEnter.shouldOperate(trade, 10));

		assertFalse(pipeEnter.shouldOperate(trade, 8));
		assertTrue(pipeEnter.shouldOperate(trade, 9));
	}
}
