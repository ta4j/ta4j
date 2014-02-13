package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.IndicatorCrossedIndicatorStrategy;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;

public class IndicatorCrossedIndicatorStrategyTest {

	@Test
	public void testCrossedIndicatorShouldBuyIndex2SellIndex4() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 4d, 7d, 9d, 6d, 3d, 2d });
		Indicator<Double> second = new MockIndicator<Double>(new Double[] { 3d, 6d, 10d, 8d, 2d, 1d });

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, second);
		Trade trade = new Trade();
		assertThat(s.shouldOperate(trade, 0)).isFalse();
		assertThat(s.shouldOperate(trade, 1)).isFalse();

		assertThat(s.shouldOperate(trade, 2)).isTrue();
		trade.operate(2);
		Operation enter = new Operation(2, OperationType.BUY);
		assertThat(trade.getEntry()).isEqualTo(enter);

		assertThat(s.shouldOperate(trade, 3)).isFalse();

		assertThat(s.shouldOperate(trade, 4)).isTrue();
		trade.operate(4);
		Operation exit = new Operation(4, OperationType.SELL);
		assertThat(trade.getExit()).isEqualTo(exit);

		assertThat(s.shouldOperate(trade, 5)).isFalse();
	}

	@Test
	public void testCrossedIndicatorShouldNotEnterWhenIndicatorsAreEquals() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 2d, 3d, 4d, 5d, 6d, 7d });
		Trade trade = new Trade();

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, first);

		for (int i = 0; i < 6; i++) {
			assertThat(s.shouldOperate(trade, i)).isFalse();
		}
	}

	@Test
	public void testCrossedIndicatorShouldNotExitWhenIndicatorsBecameEquals() {
		Indicator<Double> first = new MockIndicator<Double>(new Double[] { 4d, 7d, 9d, 6d, 3d, 2d });
		Indicator<Double> second = new MockIndicator<Double>(new Double[] { 3d, 6d, 10d, 6d, 3d, 2d });
		Trade trade = new Trade();

		Strategy s = new IndicatorCrossedIndicatorStrategy(first, second);

		Operation enter = new Operation(2, OperationType.BUY);
		assertThat(s.shouldOperate(trade, 2)).isTrue();
		trade.operate(2);
		assertThat(trade.getEntry()).isEqualTo(enter);

		for (int i = 3; i < 6; i++) {
			assertThat(s.shouldOperate(trade, i)).isFalse();
		}
	}

	@Test
	public void testEqualIndicatorsShouldNotExitWhenIndicatorsBecameEquals() {
		Indicator<Double> firstEqual = new MockIndicator<Double>(new Double[] { 2d, 1d, 4d, 5d, 6d, 7d });
		Indicator<Double> secondEqual = new MockIndicator<Double>(new Double[] { 1d, 3d, 4d, 5d, 6d, 7d });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		assertThat(s.shouldOperate(trade, 1)).isTrue();
		trade.operate(1);
		Operation enter = trade.getEntry();

		assertThat(enter).isNotNull();

		assertThat(OperationType.BUY).isEqualTo(enter.getType());

		for (int i = 2; i < 6; i++) {
			assertThat(s.shouldOperate(trade, i)).isFalse();
		}
	}

	@Test
	public void testShouldNotSellWhileIndicatorAreEquals() {
		Indicator<Double> firstEqual = new MockIndicator<Double>(new Double[] { 2d, 1d, 4d, 5d, 6d, 7d, 10d });
		Indicator<Double> secondEqual = new MockIndicator<Double>(new Double[] { 1d, 3d, 4d, 5d, 6d, 7d, 9d });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		Operation enter = new Operation(1, OperationType.BUY);

		assertThat(s.shouldOperate(trade, 1)).isTrue();
		trade.operate(1);
		assertThat(trade.getEntry()).isEqualTo(enter);

		for (int i = 2; i < 6; i++) {
			assertThat(s.shouldOperate(trade, i)).isFalse();
		}

		Operation exit = new Operation(6, OperationType.SELL);
		assertThat(s.shouldOperate(trade, 6)).isTrue();
		trade.operate(6);
		assertThat(trade.getExit()).isEqualTo(exit);
	}

	@Test
	public void testCrossShouldNotReturnNullOperations() {
		Indicator<Double> firstEqual = new MockIndicator<Double>(new Double[] { 2d, 3d, 4d, 5d, 6d, 7d, 10d });
		Indicator<Double> secondEqual = new MockIndicator<Double>(new Double[] { 1d, 3d, 4d, 5d, 6d, 7d, 9d });
		Strategy s = new IndicatorCrossedIndicatorStrategy(firstEqual, secondEqual);
		Trade trade = new Trade();

		for (int i = 0; i < 7; i++) {
			assertThat(s.shouldOperate(trade, i)).isFalse();
		}
	}
}
