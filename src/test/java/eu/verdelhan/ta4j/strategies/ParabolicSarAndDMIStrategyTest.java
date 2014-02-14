package eu.verdelhan.ta4j.strategies;

import eu.verdelhan.ta4j.strategies.ParabolicSarAndDMIStrategy;
import eu.verdelhan.ta4j.strategies.IndicatorCrossedIndicatorStrategy;
import eu.verdelhan.ta4j.strategies.IndicatorOverIndicatorStrategy;
import eu.verdelhan.ta4j.TimeSeries;
import eu.verdelhan.ta4j.indicators.simple.ClosePriceIndicator;
import eu.verdelhan.ta4j.mocks.MockTimeSeries;
import static org.assertj.core.api.Assertions.*;
import org.junit.Test;


public class ParabolicSarAndDMIStrategyTest {

	@Test
	public void shouldEnterTest()
	{
		TimeSeries series1 = new MockTimeSeries(new double[] {10, 9, 6, 10, 5 });
		TimeSeries series2 = new MockTimeSeries(new double[] {8, 7, 7, 8, 6 });
		
		IndicatorCrossedIndicatorStrategy indicatorCrossedIndicator = new IndicatorCrossedIndicatorStrategy(new ClosePriceIndicator(series1), new ClosePriceIndicator(series2));
		ParabolicSarAndDMIStrategy parabolicStrategy = new ParabolicSarAndDMIStrategy(indicatorCrossedIndicator, null);
		assertThat(parabolicStrategy.shouldEnter(0)).isFalse();
		assertThat(parabolicStrategy.shouldEnter(1)).isFalse();
		assertThat(parabolicStrategy.shouldEnter(2)).isTrue();
		assertThat(parabolicStrategy.shouldEnter(3)).isFalse();
		assertThat(parabolicStrategy.shouldEnter(4)).isTrue();
	}
	
	@Test
	public void shouldExitTest()
	{
		TimeSeries series1 = new MockTimeSeries(new double[] {6, 11, 6, 5, 9 });
		TimeSeries series2 = new MockTimeSeries(new double[] {10, 9, 7, 6, 6 });
		
		TimeSeries series3 = new MockTimeSeries(new double[] {1, 1, 1, 1, 1} );
		TimeSeries series4 = new MockTimeSeries(new double[] {2, 2, 2, 2, 0} );
		
		IndicatorCrossedIndicatorStrategy indicatorCrossedIndicator = new IndicatorCrossedIndicatorStrategy(new ClosePriceIndicator(series1), new ClosePriceIndicator(series2));
		IndicatorOverIndicatorStrategy indicatorOverIndicator = new IndicatorOverIndicatorStrategy(new ClosePriceIndicator(series3), new ClosePriceIndicator(series4));
		
		ParabolicSarAndDMIStrategy parabolicStrategy = new ParabolicSarAndDMIStrategy(indicatorCrossedIndicator, indicatorOverIndicator);
		
		assertThat(parabolicStrategy.shouldExit(0)).isFalse();
		assertThat(parabolicStrategy.shouldExit(1)).isFalse();
		assertThat(parabolicStrategy.shouldExit(2)).isFalse();
		assertThat(parabolicStrategy.shouldExit(3)).isFalse();
		assertThat(parabolicStrategy.shouldExit(4)).isTrue();
	}
}
