package eu.verdelhan.tailtest.strategy;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import eu.verdelhan.tailtest.Trade;
import eu.verdelhan.tailtest.sample.SampleIndicator;

import org.junit.Before;
import org.junit.Test;

public class PipeEnterStrategyTest {
	private SampleIndicator upper;

	private SampleIndicator lower;

	@Before
	public void setUp() {
		upper = new SampleIndicator(new double[] { 30, 32, 33, 32, 35, 33, 32, 33, 31, 30, 31, 32, 32, 34, 35 });
		lower = new SampleIndicator(new double[] { 10, 12, 13, 12, 15, 13, 12, 13, 11, 10, 11, 12, 12, 14, 15 });
	}

	@Test
	public void testFirstSellLastBuy() {
		Trade trade = new Trade();
		SampleIndicator value = new SampleIndicator(new double[] { 25, 27, 28, 27, 30, 33, 35, 37, 35, 29, 11, 10, 15,
				30, 31 });

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
		SampleIndicator value = new SampleIndicator(new double[] { 8, 15, 16, 33, 40, 45, 47, 40, 32, 25, 15, 16, 11,
				10, 12 });

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
