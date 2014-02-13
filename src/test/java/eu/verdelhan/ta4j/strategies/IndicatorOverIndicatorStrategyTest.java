package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.IndicatorOverIndicatorStrategy;
import eu.verdelhan.ta4j.Indicator;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class IndicatorOverIndicatorStrategyTest {

	private Indicator<BigDecimal> first;

	private Indicator<BigDecimal> second;

	@Before
	public void setUp() throws Exception {

		first = new MockIndicator<BigDecimal>(new BigDecimal[] {
			BigDecimal.valueOf(4),
			BigDecimal.valueOf(7),
			BigDecimal.valueOf(9),
			BigDecimal.valueOf(6),
			BigDecimal.valueOf(3),
			BigDecimal.valueOf(2)
		});
		second = new MockIndicator<BigDecimal>(new BigDecimal[] {
			BigDecimal.valueOf(3),
			BigDecimal.valueOf(6),
			BigDecimal.valueOf(10),
			BigDecimal.valueOf(8),
			BigDecimal.valueOf(2),
			BigDecimal.valueOf(1)
		});

	}

	@Test
	public void testOverIndicators() {
		Trade trade = new Trade();

		Strategy s = new IndicatorOverIndicatorStrategy(first, second);
		assertThat(s.shouldOperate(trade, 0)).isFalse();
		assertThat(s.shouldOperate(trade, 1)).isFalse();
		assertThat(trade.getEntry()).isEqualTo(null);
		Operation buy = new Operation(2, OperationType.BUY);
		assertThat(s.shouldOperate(trade, 2)).isTrue();
		trade.operate(2);
		assertThat(trade.getEntry()).isEqualTo(buy);
		trade = new Trade();
		buy = new Operation(3, OperationType.BUY);
		assertThat(s.shouldOperate(trade, 3)).isTrue();
		trade.operate(3);
		assertThat(trade.getEntry()).isEqualTo(buy);

		assertThat(s.shouldOperate(trade, 3)).isFalse();

		Operation sell = new Operation(4, OperationType.SELL);
		assertThat(s.shouldOperate(trade, 4)).isTrue();
		trade.operate(4);
		assertThat(trade.getExit()).isEqualTo(sell);

	}
}
