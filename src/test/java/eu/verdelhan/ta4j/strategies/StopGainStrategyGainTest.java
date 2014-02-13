package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.StopGainStrategy;
import eu.verdelhan.ta4j.strategies.JustBuyOnceStrategy;
import eu.verdelhan.ta4j.Operation;
import eu.verdelhan.ta4j.OperationType;
import eu.verdelhan.ta4j.Strategy;
import eu.verdelhan.ta4j.Trade;
import eu.verdelhan.ta4j.mocks.MockIndicator;
import eu.verdelhan.ta4j.mocks.MockStrategy;
import static org.assertj.core.api.Assertions.*;
import org.junit.Before;
import org.junit.Test;

public class StopGainStrategyGainTest {

	private MockIndicator<Double> indicator;

	@Before
	public void setUp() throws Exception {
		indicator = new MockIndicator<Double>(new Double[] { 100d, 98d, 103d, 115d, 107d });
	}

	@Test
	public void testStopperShouldSell() {

		Strategy justBuy = new JustBuyOnceStrategy();
		Strategy stopper = new StopGainStrategy(indicator, justBuy, 4);

		Operation buy = new Operation(0, OperationType.BUY);
		Operation sell = new Operation(3, OperationType.SELL);

		Trade trade = new Trade();
		assertThat(stopper.shouldOperate(trade, 0)).isTrue();
		trade.operate(0);
		assertThat(trade.getEntry()).isEqualTo(buy);
		assertThat(stopper.shouldOperate(trade, 1)).isFalse();
		assertThat(stopper.shouldOperate(trade, 2)).isFalse();

		assertThat(stopper.shouldOperate(trade, 3)).isTrue();
		trade.operate(3);
		assertThat(trade.getExit()).isEqualTo(sell);
	}

	@Test
	public void testStopperShouldSellIfStrategySays() {

		Operation[] enter = new Operation[] { new Operation(0, OperationType.BUY), null, new Operation(2, OperationType.BUY), null, null };
		Operation[] exit = new Operation[] { null, new Operation(1, OperationType.SELL), null, null, new Operation(4, OperationType.SELL) };

		Strategy sell1 = new MockStrategy(enter, exit);

		Strategy stopper = new StopGainStrategy(indicator, sell1, 5);

		Operation buy = new Operation(0, OperationType.BUY);
		Operation sell = new Operation(1, OperationType.SELL);

		Trade trade = new Trade();
		assertThat(stopper.shouldOperate(trade, 0)).isTrue();
		trade.operate(0);

		assertThat(trade.getEntry()).isEqualTo(buy);

		assertThat(stopper.shouldOperate(trade, 1)).isTrue();
		trade.operate(1);

		assertThat(trade.getExit()).isEqualTo(sell);
		
		trade = new Trade();
		buy = new Operation(2, OperationType.BUY);
		sell = new Operation(3, OperationType.SELL);
		
		assertThat(stopper.shouldOperate(trade, 2)).isTrue();
		trade.operate(2);

		assertThat(trade.getEntry()).isEqualTo(buy);

		assertThat(stopper.shouldOperate(trade, 3)).isTrue();
		trade.operate(3);

		assertThat(trade.getExit()).isEqualTo(sell);
	}

}
