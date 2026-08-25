/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;

/**
 * Unit tests for {@link BlockBootstrapNulls} member generation and return
 * computation edge cases that live outside the double range.
 */
class BlockBootstrapNullsTest {

    @Test
    void memberClosesStayFiniteWhenSampledReturnSpansDoubleRange() {
        // exp(ln(MAX/MIN)) itself exceeds double range; reconstruction must go
        // through the accumulated log-close so a finite source path can never
        // materialize an infinite close (or downstream NaN) in null members.
        final BarSeries source = new BaseBarSeriesBuilder().withName("member-overflow")
                .withNumFactory(org.ta4j.core.num.DoubleNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        final double[] closes = { Double.MIN_VALUE, Double.MAX_VALUE };
        for (int index = 0; index < closes.length; index++) {
            final Num close = DoubleNum.valueOf(closes[index]);
            source.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(close)
                    .highPrice(close)
                    .lowPrice(close)
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }

        // Two bars -> exactly one log return, so every member draws it.
        for (final BarSeries member : BlockBootstrapNulls.generate(source, 1, 8, 11L)) {
            for (int offset = 0; offset < 2; offset++) {
                final Num close = member.getBar(offset).getClosePrice();
                // The contract is a finite positive close; a reconstruction
                // equal to Double.MAX_VALUE itself stays representable.
                final double narrowed = close.doubleValue();
                assertTrue(Double.isFinite(narrowed) && narrowed > 0d,
                        "member close at bar " + offset + " not representable: " + close);
            }
        }
    }

    @Test
    void memberGenerationRejectsCumulativePathBeyondDoubleRange() {
        // Two individually representable +598 returns compound past double max;
        // the second step must fail loud instead of materializing Infinity.
        final BarSeries source = source3Bar("cumulative-overflow");
        assertThrows(IllegalStateException.class,
                () -> BlockBootstrapNulls.generateMember(source, new double[] { 598.8d, 598.8d }, 8, 7L, 0));
    }

    @Test
    void memberGenerationRejectsSteepNegativeReturnBelowDoubleRange() {
        // A steep negative sampled return from a tiny start accumulates below
        // double range; expNum's reciprocal collapses to zero and must be
        // rejected instead of recording a non-positive close.
        final BarSeries source = doubleSeries("steep-negative", Double.MIN_VALUE);
        assertThrows(IllegalStateException.class,
                () -> BlockBootstrapNulls.generateMember(source, new double[] { -3000d }, 8, 7L, 0));
    }

    @Test
    void memberClosesStayRepresentableOnFlatMinValueSource() {
        // A flat MIN_VALUE source draws zero returns; every member close equals
        // MIN_VALUE. The accumulated log-close sits near -744, but the direct
        // product is representable and must win over the collapsing fallback.
        final BarSeries source = doubleSeries("flat-min", Double.MIN_VALUE);
        for (final BarSeries member : BlockBootstrapNulls.generate(source, 1, 8, 13L)) {
            for (int offset = 0; offset < member.getBarCount(); offset++) {
                final double close = member.getBar(offset).getClosePrice().doubleValue();
                assertTrue(Double.isFinite(close) && close > 0d,
                        "member close at bar " + offset + " not representable: " + close);
            }
        }
    }

    @Test
    void intrabarScalingStaysFiniteWhenProductOverflows() {
        // wick * memberClose would overflow even though the mathematically
        // exact scaled value is representable: ratio first, then multiply.
        final Num scaled = BlockBootstrapNulls.scaled(DoubleNum.valueOf(1e308), DoubleNum.valueOf(Double.MAX_VALUE),
                DoubleNum.valueOf(Double.MAX_VALUE));
        assertEquals(1e308d, scaled.doubleValue(), 1e292d);
    }

    @Test
    void memberBarsStayFiniteOnFlatMaxValueSource() {
        // Every OHLC value of a flat MAX_VALUE source equals its close, so the
        // intrabar scale factor is exactly one; the short-circuit must keep
        // wicks finite instead of overflowing MAX * MAX / MAX to infinity.
        final BarSeries source = doubleSeries("flat-max", Double.MAX_VALUE);
        for (final BarSeries member : BlockBootstrapNulls.generate(source, 1, 8, 17L)) {
            for (int offset = 0; offset < member.getBarCount(); offset++) {
                final Bar bar = member.getBar(offset);
                for (final Num price : new Num[] { bar.getOpenPrice(), bar.getHighPrice(), bar.getLowPrice(),
                        bar.getClosePrice() }) {
                    assertTrue(
                            price.isPositive()
                                    && (!(price.getDelegate() instanceof Double delegate) || Double.isFinite(delegate)),
                            "non-representable price " + price + " at bar " + offset);
                }
            }
        }
    }

    @Test
    void logReturnsRejectsNonFiniteDoubleNumClose() {
        // DoubleNumFactory accepts POSITIVE_INFINITY and isPositive() is true;
        // feeding an infinite close into the decomposed logarithm would scale
        // it by 1e300 forever instead of failing loud.
        final BarSeries source = doubleSeries("infinite-close", Double.POSITIVE_INFINITY);
        assertThrows(IllegalArgumentException.class, () -> BlockBootstrapNulls.logReturns(source));
    }

    private static BarSeries doubleSeries(final String name, final double close) {
        return doubleSeries(name, close, 2);
    }

    private static BarSeries source3Bar(final String name) {
        return doubleSeries(name, 1d, 3);
    }

    private static BarSeries doubleSeries(final String name, final double close, final int barCount) {
        final BarSeries series = new BaseBarSeriesBuilder().withName(name)
                .withNumFactory(org.ta4j.core.num.DoubleNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        final Num closeNum = DoubleNum.valueOf(close);
        for (int index = 1; index <= barCount; index++) {
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index)))
                    .openPrice(closeNum)
                    .highPrice(closeNum)
                    .lowPrice(closeNum)
                    .closePrice(closeNum)
                    .volume(1)
                    .amount(closeNum)
                    .trades(1)
                    .add();
        }
        return series;
    }
}
