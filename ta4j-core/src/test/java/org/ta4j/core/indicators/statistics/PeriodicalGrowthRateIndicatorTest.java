package org.ta4j.core.indicators.statistics;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.*;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.mocks.MockTimeSeries;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.CrossedDownIndicatorRule;
import org.ta4j.core.trading.rules.CrossedUpIndicatorRule;

import java.util.function.Function;

import static org.junit.Assert.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;


public class PeriodicalGrowthRateIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private TimeSeriesManager seriesManager;
    
    private ClosePriceIndicator closePrice;

    public PeriodicalGrowthRateIndicatorTest(Function<Number, Num> numFunction) {
        super(numFunction);
    }

    @Before
    public void setUp() {
        TimeSeries mockSeries = new MockTimeSeries(numFunction,
                29.49, 28.30, 27.74, 27.65, 27.60, 28.70, 28.60,
                28.19, 27.40, 27.20, 27.28, 27.00, 27.59, 26.20,
                25.75, 24.75, 23.33, 24.45, 24.25, 25.02, 23.60,
                24.20, 24.28, 25.70, 25.46, 25.10, 25.00, 25.00,
                25.85);
        seriesManager = new TimeSeriesManager(mockSeries);
        closePrice = new ClosePriceIndicator(mockSeries);
    }

    @Test
    public void testGetTotalReturn() { 
        PeriodicalGrowthRateIndicator gri = new PeriodicalGrowthRateIndicator(this.closePrice, 5);
        Num result = gri.getTotalReturn();
        assertNumEquals(0.9564, result);
    }
    
    @Test
    public void testCalculation() { 
        PeriodicalGrowthRateIndicator gri = new PeriodicalGrowthRateIndicator(this.closePrice,5);
        
        assertEquals(gri.getValue(0),NaN);
        assertEquals(gri.getValue(4), NaN);
        assertNumEquals(-0.0268, gri.getValue(5));
        assertNumEquals(0.0541, gri.getValue(6));
        assertNumEquals(-0.0495, gri.getValue(10));
        assertNumEquals(0.2009, gri.getValue(21));
        assertNumEquals(0.0220, gri.getValue(24));
        assertEquals(gri.getValue(25), NaN);
        assertEquals(gri.getValue(26), NaN);
    }
    
    @Test
    public void testStrategies() { 
        
        PeriodicalGrowthRateIndicator gri = new PeriodicalGrowthRateIndicator(this.closePrice,5);

        // Rules
        Rule buyingRule = new CrossedUpIndicatorRule(gri, 0);
        Rule sellingRule = new CrossedDownIndicatorRule(gri, 0);
        
        Strategy strategy = new BaseStrategy(buyingRule, sellingRule);
                
        // Check trades
        int result = seriesManager.run(strategy).getTradeCount();             
        int expResult = 3;
        
        assertEquals(expResult, result);
    }
}
