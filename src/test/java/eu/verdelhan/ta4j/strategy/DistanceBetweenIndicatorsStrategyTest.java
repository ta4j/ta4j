package eu.verdelhan.ta4j.strategy;

import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class DistanceBetweenIndicatorsStrategyTest {
	private MockIndicator<Double> upper;

	private MockIndicator<Double> lower;

	private AbstractStrategy distanceEnter;

	@Before
	public void setUp() {
		upper = new MockIndicator<Double>(new Double[] { 30d, 32d, 33d, 32d, 35d, 33d, 32d });
		lower = new MockIndicator<Double>(new Double[] { 10d, 10d, 10d, 12d, 14d, 15d, 15d });
		distanceEnter = new DistanceBetweenIndicatorsStrategy(upper, lower, 20, 0.1);
	}

	@Test
	public void testStrategyIsBuyingCorreclty() {
		Trade trade = new Trade();

		assertThat(distanceEnter.shouldOperate(trade, 0)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 1)).isTrue();
		trade = new Trade();
		assertThat(distanceEnter.shouldOperate(trade, 2)).isTrue();
		assertThat(distanceEnter.shouldOperate(trade, 3)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 4)).isFalse();
	}

	@Test
	public void testStrategyIsSellingCorrectly() {
		Trade trade = new Trade();
		trade.operate(2);

		assertThat(distanceEnter.shouldOperate(trade, 0)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 5)).isTrue();

		trade = new Trade();
		trade.operate(2);

		assertThat(distanceEnter.shouldOperate(trade, 6)).isTrue();
		assertThat(distanceEnter.shouldOperate(trade, 3)).isFalse();
		assertThat(distanceEnter.shouldOperate(trade, 4)).isFalse();

	}
}
