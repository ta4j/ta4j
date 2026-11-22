/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.statistics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.ta4j.core.TestUtils.assertNumEquals;
import static org.ta4j.core.num.NaN.NaN;

import java.math.MathContext;
import java.math.RoundingMode;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.backtest.BarSeriesManager;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;

public class PeriodicalGrowthRateIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeriesManager seriesManager;

    private ClosePriceIndicator closePrice;

    public PeriodicalGrowthRateIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @BeforeEach
    public void setUp() {
        var mockSeries = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(29.49, 28.30, 27.74, 27.65, 27.60, 28.70, 28.60, 28.19, 27.40, 27.20, 27.28, 27.00, 27.59,
                        26.20, 25.75, 24.75, 23.33, 24.45, 24.25, 25.02, 23.60, 24.20, 24.28, 25.70, 25.46, 25.10,
                        25.00, 25.00, 25.85)
                .build();
        seriesManager = new BarSeriesManager(mockSeries);
        closePrice = new ClosePriceIndicator(mockSeries);
    }

    @Test
    public void testGetTotalReturn() {
        var gri = new PeriodicalGrowthRateIndicator(this.closePrice, 5);
        Num result = gri.getTotalReturn();
        assertNumEquals(0.9564, result);
    }

    @Test
    public void testCalculation() {
        var gri = new PeriodicalGrowthRateIndicator(this.closePrice, 5);

        assertEquals(gri.getValue(0), NaN);
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
    public void completeTimeframesUseExactIntegerBoundariesAtLowDecimalPrecision() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance(1))
                .withData(100, 100, 100, 100, 100, 100, 100, 100)
                .build();
        var growth = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(series), 3);

        assertNumEquals(0, growth.getValue(5));
        assertEquals(NaN, growth.getValue(6));
        assertEquals(NaN, growth.getValue(7));

        series.barBuilder().openPrice(100).closePrice(100).highPrice(100).lowPrice(100).volume(0).add();
        assertNumEquals(0, growth.getValue(6));
    }

    @Test
    public void incompleteTimeframeIsRecomputedThroughDownstreamCacheAfterAppend() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100, 100, 100, 100, 100, 100, 100, 100)
                .build();
        int[] warmupCalculations = { 0 };
        var growth = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(series), 3) {
            @Override
            protected Num calculate(int index) {
                if (index == 1) {
                    warmupCalculations[0]++;
                }
                return super.calculate(index);
            }
        };
        CachedIndicator<Num> downstream = new CachedIndicator<>(growth) {
            @Override
            protected Num calculate(int index) {
                return growth.getValue(index);
            }

            @Override
            public int getCountOfUnstableBars() {
                return growth.getCountOfUnstableBars();
            }
        };

        assertEquals(NaN, growth.getValue(1));
        assertEquals(NaN, growth.getValue(1));
        assertEquals(1, warmupCalculations[0]);
        assertEquals(NaN, growth.getValue(6));
        assertEquals(NaN, downstream.getValue(6));

        series.barBuilder().openPrice(100).closePrice(100).highPrice(100).lowPrice(100).volume(0).add();

        assertNumEquals(0, growth.getValue(6));
        assertNumEquals(0, downstream.getValue(6));
    }

    @Test
    public void supportsNonDecimalNumericFactories() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(SinglePrecisionNumFactory.getInstance())
                .withData(100, 100, 100, 100, 100, 100, 100, 100)
                .build();
        var growth = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(series), 3);

        assertNumEquals(0, growth.getValue(5));
        assertEquals(NaN, growth.getValue(6));
    }

    @Test
    public void unlimitedDecimalPrecisionHandlesRepeatingFractions() {
        BarSeries series = new MockBarSeriesBuilder()
                .withNumFactory(DecimalNumFactory.getInstance(MathContext.UNLIMITED))
                .withData(100, 100, 100, 100, 100, 100, 100, 100, 200, 100, 100, 100, 100, 100, 100, 100, 100, 100)
                .build();
        var growth = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(series), 5);

        assertNumEquals(numOf(Math.pow(2, 5.0 / 3) - 1), growth.getValue(8), 1e-12);
        // Three complete timeframes require a non-terminating 1/3 exponent.
        assertNumEquals(1, growth.getTotalReturn());
    }

    @Test
    public void strictRoundingDoesNotThrowOnRepeatingReturns() {
        for (int precision : new int[] { 0, 10 }) {
            NumFactory factory = DecimalNumFactory.getInstance(new MathContext(precision, RoundingMode.UNNECESSARY));
            BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(3, 4, 4, 6, 6).build();
            var growth = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(series), 2);

            assertEquals(NaN, growth.getValue(2));
            assertNumEquals(1.25, growth.getValue(3));
            assertEquals(NaN, growth.getTotalReturn());
        }
    }

    @Test
    public void nonFinitePricesAreUndefinedAtEitherEndOfAPeriod() {
        DoubleNumFactory factory = DoubleNumFactory.getInstance();
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(factory).withData(1, 1, 1, 1).build();
        Num infinity = DoubleNum.valueOf(Double.POSITIVE_INFINITY);
        var prices = new FixedIndicator<Num>(series, infinity, factory.hundred(), factory.hundred(), infinity);
        var growth = new PeriodicalGrowthRateIndicator(prices, 2);

        assertEquals(NaN, growth.getValue(2));
        assertEquals(NaN, growth.getValue(3));
    }

    @Test
    public void totalReturnIsUndefinedWhenCompleteReturnsOverflowInAggregate() {
        double growthFactor = 0x1.0p600;
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DoubleNumFactory.getInstance())
                .withData(1 / growthFactor, 1, 1, 1, growthFactor, 1, 1)
                .build();
        var growth = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(series), 2);

        assertNumEquals(growthFactor, growth.getValue(2));
        assertNumEquals(growthFactor, growth.getValue(4));
        assertEquals(NaN, growth.getTotalReturn());
    }

    @Test
    public void totalReturnDistinguishesAggregateUnderflowFromZeroGrowthFactor() {
        double[] prices = new double[23];
        for (int i = 0; i < prices.length; i++) {
            prices[i] = Math.scalb(1.0, 1000 - 50 * i);
        }
        NumFactory factory = DoubleNumFactory.getInstance();
        BarSeries tinyFactors = new MockBarSeriesBuilder().withNumFactory(factory).withData(prices).build();
        BarSeries zeroFactor = new MockBarSeriesBuilder().withNumFactory(factory).withData(100, 0).build();
        var underflows = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(tinyFactors), 1);
        var goesToZero = new PeriodicalGrowthRateIndicator(new ClosePriceIndicator(zeroFactor), 1);

        assertEquals(NaN, underflows.getTotalReturn());
        assertNumEquals(0, goesToZero.getTotalReturn());
    }

    @Test
    public void testStrategies() {

        var gri = new PeriodicalGrowthRateIndicator(this.closePrice, 5);

        // Rules
        Rule buyingRule = new CrossedUpIndicatorRule(gri, 0);
        Rule sellingRule = new CrossedDownIndicatorRule(gri, 0);

        Strategy strategy = new BaseStrategy(buyingRule, sellingRule);

        // Check positions
        int result = seriesManager.run(strategy).getPositionCount();
        int expResult = 3;

        assertEquals(expResult, result);
    }
}
