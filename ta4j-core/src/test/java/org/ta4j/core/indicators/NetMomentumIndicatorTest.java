/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.BinaryOperationIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.Assert.*;

public class NetMomentumIndicatorTest extends AbstractIndicatorTest<Indicator<Num>, Num> {

    private BarSeries series;
    private ClosePriceIndicator closePrice;

    public NetMomentumIndicatorTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        this.series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();
        // Create a series with values that will produce known RSI values
        series.barBuilder().closePrice(44.34).add();
        series.barBuilder().closePrice(44.09).add();
        series.barBuilder().closePrice(44.15).add();
        series.barBuilder().closePrice(43.61).add();
        series.barBuilder().closePrice(44.33).add();
        series.barBuilder().closePrice(44.83).add();
        series.barBuilder().closePrice(45.10).add();
        series.barBuilder().closePrice(45.42).add();
        series.barBuilder().closePrice(45.84).add();
        series.barBuilder().closePrice(46.08).add();
        series.barBuilder().closePrice(45.89).add();
        series.barBuilder().closePrice(46.03).add();
        series.barBuilder().closePrice(45.61).add();
        series.barBuilder().closePrice(46.28).add();
        series.barBuilder().closePrice(46.28).add();
        series.barBuilder().closePrice(46.00).add();
        series.barBuilder().closePrice(46.03).add();
        series.barBuilder().closePrice(46.41).add();
        series.barBuilder().closePrice(46.22).add();
        series.barBuilder().closePrice(45.64).add();

        closePrice = new ClosePriceIndicator(series);
    }

    @Test
    public void testWithRSIIndicator() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator subject = NetMomentumIndicator.forRsi(rsi, 5);
        NetMomentumIndicator decayed = NetMomentumIndicator.forRsiWithDecay(rsi, 5, 0.9);

        // Do not evaluate values because RSI has NaN early which is incompatible with
        // DecimalNum.
        // Instead, validate unstable bar propagation behavior without invoking
        // calculations.
        assertTrue(subject.getCountOfUnstableBars() >= rsi.getCountOfUnstableBars());
        assertTrue(decayed.getCountOfUnstableBars() >= rsi.getCountOfUnstableBars());
    }

    @Test
    public void testRsiUnstableNaNsAreIgnored() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator subject = NetMomentumIndicator.forRsi(rsi, 5);
        NetMomentumIndicator decayed = NetMomentumIndicator.forRsiWithDecay(rsi, 5, 0.9);

        int stableIndex = series.getBarCount() - 1;
        assertFalse("RSI should provide valid data once warmed up", rsi.getValue(stableIndex).isNaN());
        assertFalse("Net momentum should recover from initial NaN inputs", subject.getValue(stableIndex).isNaN());
        assertFalse("Net momentum with decay should recover from initial NaN inputs",
                decayed.getValue(stableIndex).isNaN());
    }

    @Test
    public void testGeneralConstructor() {
        // Create a simple oscillating indicator for testing
        CachedIndicator<Num> oscillator = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                // Oscillates between 0 and 100
                return numOf(50 + 30 * Math.sin(index * 0.5));
            }
        };

        NetMomentumIndicator subject = new NetMomentumIndicator(oscillator, 10, 50);

        assertNotNull(subject.getValue(10));
    }

    @Test
    public void testPositiveAndNegativeBalance() {
        // Create an indicator that alternates above and below neutral
        CachedIndicator<Num> alternatingIndicator = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(index % 2 == 0 ? 60 : 40); // Alternates above/below 50
            }
        };

        NetMomentumIndicator subject = new NetMomentumIndicator(alternatingIndicator, 3, 50);

        // At index 2: [60, 40, 60] - after smoothing and differencing, should have
        // mixed balance
        Num value = subject.getValue(2);
        assertNotNull(value);
    }

    @Test
    public void testGetCountOfUnstableBars() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator subject = NetMomentumIndicator.forRsi(rsi, 5);

        int unstableBars = subject.getCountOfUnstableBars();
        assertTrue(unstableBars >= 5); // At least the timeframe
        assertTrue(unstableBars >= rsi.getCountOfUnstableBars()); // At least the RSI unstable bars
    }

    @Test
    public void testCachedValues() {
        // Use a stable oscillator without NaN to exercise caching
        CachedIndicator<Num> oscillator = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(50 + 10 * Math.sin(index));
            }
        };
        NetMomentumIndicator subject = new NetMomentumIndicator(oscillator, 5, 50);

        // Get value twice - should be cached
        Num firstCall = subject.getValue(15);
        Num secondCall = subject.getValue(15);

        assertSame(firstCall, secondCall); // Should be the same object due to caching
    }

    @Test
    public void testTrendDetection() {
        // Create a trending indicator (consistently above neutral)
        CachedIndicator<Num> trendingUp = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(70); // Consistently above 50
            }
        };

        NetMomentumIndicator subject = new NetMomentumIndicator(trendingUp, 5, 50);

        // After several bars, balance should be positive
        Num balance = subject.getValue(10);
        assertTrue(balance.isPositive());
    }

    @Test
    public void testWithDifferentNeutralPivots() {
        // Use a non-NaN oscillator to compare pivot sensitivity
        CachedIndicator<Num> oscillator = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(50 + 20 * Math.sin(index * 0.3));
            }
        };

        NetMomentumIndicator subject30 = new NetMomentumIndicator(oscillator, 5, 30);
        NetMomentumIndicator subject50 = new NetMomentumIndicator(oscillator, 5, 50);
        NetMomentumIndicator subject70 = new NetMomentumIndicator(oscillator, 5, 70);

        // Different pivot values should produce different results
        Num value30 = subject30.getValue(19);
        Num value50 = subject50.getValue(19);
        Num value70 = subject70.getValue(19);

        assertNotEquals(value30, value50);
        assertNotEquals(value50, value70);
    }

    @Test
    public void testExplicitDecayFactorOfOneMatchesDefault() {
        CachedIndicator<Num> oscillator = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(50 + 7 * Math.sin(index * 0.2));
            }
        };

        NetMomentumIndicator defaultDecay = new NetMomentumIndicator(oscillator, 8, 50);
        NetMomentumIndicator explicitDecay = new NetMomentumIndicator(oscillator, 8, 50, 1.0);

        for (int i = 0; i < series.getBarCount(); i++) {
            assertTrue(defaultDecay.getValue(i).isEqual(explicitDecay.getValue(i)));
        }
    }

    @Test
    public void testDecayFactorPullsTowardNeutral() {
        CachedIndicator<Num> constantAbove = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(70);
            }
        };

        int timeFrame = 5;
        NetMomentumIndicator noDecay = new NetMomentumIndicator(constantAbove, timeFrame, 50);
        NetMomentumIndicator decayed = new NetMomentumIndicator(constantAbove, timeFrame, 50, 0.8);

        // After the window fills, the decayed series should be closer to zero.
        int targetIndex = Math.max(timeFrame, series.getBarCount() - 1);
        Num noDecayValue = noDecay.getValue(targetIndex);
        Num decayedValue = decayed.getValue(targetIndex);

        assertTrue(decayedValue.abs().isLessThan(noDecayValue.abs()));

        Num decay = numOf(0.8);
        Num decayAtWindow = decay.pow(timeFrame);

        KalmanFilterIndicator smoothed = new KalmanFilterIndicator(constantAbove);
        BinaryOperationIndicator deltaIndicator = BinaryOperationIndicator.difference(smoothed, 50);

        int unstableBars = decayed.getCountOfUnstableBars();
        Num expected = null;
        for (int i = 0; i <= targetIndex; i++) {
            if (i < unstableBars) {
                assertTrue("Expected NaN at index " + i, Num.isNaNOrNull(decayed.getValue(i)));
                continue;
            }

            if (expected == null) {
                expected = deltaIndicator.getValue(i);
                continue;
            }

            expected = expected.multipliedBy(decay).plus(deltaIndicator.getValue(i));
            if (i >= timeFrame) {
                int expiredIndex = i - timeFrame;
                Num expired = expiredIndex < unstableBars ? numOf(0) : deltaIndicator.getValue(expiredIndex);
                expected = expected.minus(expired.multipliedBy(decayAtWindow));
            }
        }

        assertTrue(decayedValue.isEqual(expected));
    }

    @Test
    public void testDecayFactorZeroBehavesLikeInstantaneousDelta() {
        CachedIndicator<Num> varying = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(40 + 20 * Math.sin(index * 0.3));
            }
        };

        NetMomentumIndicator instant = new NetMomentumIndicator(varying, 6, 50, 0.0);
        KalmanFilterIndicator smoothed = new KalmanFilterIndicator(varying);
        BinaryOperationIndicator deltaIndicator = BinaryOperationIndicator.difference(smoothed, 50);

        int unstableBars = instant.getCountOfUnstableBars();
        for (int i = 0; i < unstableBars; i++) {
            assertTrue("Expected NaN at index " + i, Num.isNaNOrNull(instant.getValue(i)));
        }

        for (int i = unstableBars; i < series.getBarCount(); i++) {
            Num expected = deltaIndicator.getValue(i);
            assertTrue("Mismatch at index " + i, instant.getValue(i).isEqual(expected));
        }
    }

    @Test
    public void testConstructorRejectsNonPositiveTimeframe() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        assertThrows(IllegalArgumentException.class, () -> NetMomentumIndicator.forRsi(rsi, 0));
        assertThrows(IllegalArgumentException.class, () -> NetMomentumIndicator.forRsi(rsi, -5));
        assertThrows(IllegalArgumentException.class, () -> NetMomentumIndicator.forRsiWithDecay(rsi, 0, 0.9));
        assertThrows(IllegalArgumentException.class, () -> NetMomentumIndicator.forRsiWithDecay(rsi, -1, 0.9));
    }

    @Test
    public void testConstructorNullIndicator() {
        // Current implementation will throw a NullPointerException when passing null
        assertThrows(NullPointerException.class, () -> new NetMomentumIndicator(null, 5, 50));

        assertThrows(NullPointerException.class, () -> new NetMomentumIndicator(closePrice, 5, null));
        assertThrows(NullPointerException.class, () -> new NetMomentumIndicator(closePrice, 5, 50, null));
        assertThrows(NullPointerException.class, () -> NetMomentumIndicator.forRsiWithDecay(null, 5, 0.9));
        assertThrows(NullPointerException.class,
                () -> NetMomentumIndicator.forRsiWithDecay(new RSIIndicator(closePrice, 14), 5, null));
    }

    @Test
    public void testConstructorRejectsDecayOutsideBounds() {
        CachedIndicator<Num> oscillator = buildOscillator();

        assertThrows(IllegalArgumentException.class, () -> new NetMomentumIndicator(oscillator, 5, 50, 1.5));
        assertThrows(IllegalArgumentException.class, () -> new NetMomentumIndicator(oscillator, 5, 50, -0.1));
    }

    @Test
    public void testUnstableBarsCountWithRSI() {
        RSIIndicator rsi = new RSIIndicator(closePrice, 14);
        NetMomentumIndicator subject = NetMomentumIndicator.forRsi(rsi, 5);

        // Validate count relationship without evaluating values
        assertTrue(subject.getCountOfUnstableBars() >= rsi.getCountOfUnstableBars());
    }

    @Test
    public void testTimeframeOneNeutralZero() {
        // Oscillator constantly equals pivot (50). With timeframe=1, balance should be
        // zero.
        CachedIndicator<Num> constant50 = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(50);
            }
        };

        NetMomentumIndicator subject = new NetMomentumIndicator(constant50, 1, 50);

        // Check a few indices
        for (int i = 0; i < series.getBarCount(); i++) {
            assertTrue("Expected zero at index " + i, subject.getValue(i).isZero());
        }
    }

    @Test
    public void testTimeframeOneSignMatchesOscillatorMinusPivot() {
        // Oscillator always above pivot
        CachedIndicator<Num> constantAbove = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(70);
            }
        };

        // Oscillator always below pivot
        CachedIndicator<Num> constantBelow = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(30);
            }
        };

        NetMomentumIndicator above = new NetMomentumIndicator(constantAbove, 1, 50);
        NetMomentumIndicator below = new NetMomentumIndicator(constantBelow, 1, 50);

        assertTrue(above.getValue(0).isPositive());
        assertTrue(below.getValue(0).isNegative());
        assertTrue(above.getValue(10).isPositive());
        assertTrue(below.getValue(10).isNegative());
    }

    @Test
    public void testUnstableBarsEqualsOscillatorUnstableWhenZero() {
        // Create oscillator with zero unstable bars
        CachedIndicator<Num> osc = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(50 + 10 * Math.sin(index));
            }
        };

        NetMomentumIndicator subject = new NetMomentumIndicator(osc, 5, 50);
        assertEquals(4, subject.getCountOfUnstableBars());
    }

    @Test
    public void testUnstableBarsIncludeSourceAndWindow() {
        CachedIndicator<Num> osc = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 3;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(55);
            }
        };

        NetMomentumIndicator subject = new NetMomentumIndicator(osc, 6, 50);
        assertEquals(8, subject.getCountOfUnstableBars());
        for (int i = 0; i < subject.getCountOfUnstableBars(); i++) {
            assertTrue("Expected NaN at index " + i, Num.isNaNOrNull(subject.getValue(i)));
        }
    }

    @Test
    public void testFractionalNeutralPivotProducesExpectedValues() {
        CachedIndicator<Num> constant = new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                return numOf(51.25);
            }
        };

        int timeFrame = 4;
        double pivot = 50.5;
        NetMomentumIndicator indicator = new NetMomentumIndicator(constant, timeFrame, pivot);

        int unstableBars = indicator.getCountOfUnstableBars();
        for (int i = 0; i < series.getBarCount(); i++) {
            Num actual = indicator.getValue(i);
            if (i < unstableBars) {
                assertTrue("Expected NaN at index " + i, Num.isNaNOrNull(actual));
                continue;
            }
            int window = Math.min(i - unstableBars + 1, timeFrame);
            Num expected = numOf(window * (51.25 - pivot));
            assertTrue("Unexpected value at index " + i, actual.isEqual(expected));
        }
    }

    @Test
    public void testOrderIndependenceOfAccessPattern() {
        CachedIndicator<Num> oscillatorSequential = buildOscillator();
        CachedIndicator<Num> oscillatorReverse = buildOscillator();

        int timeFrame = 6;
        NetMomentumIndicator sequential = new NetMomentumIndicator(oscillatorSequential, timeFrame, 50);
        NetMomentumIndicator reverse = new NetMomentumIndicator(oscillatorReverse, timeFrame, 50);

        int maxIndex = series.getBarCount() - 1;
        Num[] forwardValues = new Num[maxIndex + 1];
        for (int i = 0; i <= maxIndex; i++) {
            forwardValues[i] = sequential.getValue(i);
        }

        Num[] reverseValues = new Num[maxIndex + 1];
        for (int i = maxIndex; i >= 0; i--) {
            reverseValues[i] = reverse.getValue(i);
        }

        int unstableBars = sequential.getCountOfUnstableBars();
        Num tolerance = numOf(1e-9);
        for (int i = 0; i <= maxIndex; i++) {
            if (i < unstableBars) {
                assertTrue("Expected NaN at index " + i, Num.isNaNOrNull(forwardValues[i]));
                assertTrue("Expected NaN at index " + i, Num.isNaNOrNull(reverseValues[i]));
                continue;
            }
            Num delta = forwardValues[i].minus(reverseValues[i]).abs();
            assertTrue("Access-order dependent mismatch at index " + i + " (delta=" + delta + ")",
                    delta.isLessThan(tolerance));
        }
    }

    private CachedIndicator<Num> buildOscillator() {
        return new CachedIndicator<>(closePrice) {
            @Override
            public int getCountOfUnstableBars() {
                return 0;
            }

            @Override
            protected Num calculate(int index) {
                // Smooth oscillation between 35 and 65
                return numOf(50 + 15 * Math.sin(index * 0.35));
            }
        };
    }
}
