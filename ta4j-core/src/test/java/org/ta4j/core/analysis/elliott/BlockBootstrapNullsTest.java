/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBarSeriesBuilder;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.DecimalNumFactory;
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
                .withNumFactory(DoubleNumFactory.getInstance())
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
    void memberGenerationKeepsRepresentableTinyCloseAfterSteepNegativeReturn() {
        final BarSeries source = doubleSeries("near-underflow-negative", Double.MAX_VALUE);
        final double returnToMinValue = Math.log(Double.MIN_VALUE) - Math.log(Double.MAX_VALUE);
        final BarSeries member = BlockBootstrapNulls.generateMember(source, new double[] { returnToMinValue }, 8, 7L,
                0);
        final double close = member.getBar(1).getClosePrice().doubleValue();
        assertTrue(Double.isFinite(close) && close > 0d, "representable tiny close was lost: " + close);
    }

    @Test
    void memberGenerationRejectsSteepNegativeReturnBelowDoubleRange() {
        // A steep negative sampled return from a tiny start accumulates below
        // double range; direct negative exponentiation still underflows to zero
        // and must be rejected instead of recording a non-positive close.
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
    void intrabarScalingRetriesAfterRatioOverflow() {
        final Num scaled = BlockBootstrapNulls.scaled(DoubleNum.valueOf(Double.MAX_VALUE),
                DoubleNum.valueOf(Double.MIN_VALUE), DoubleNum.valueOf(Double.MIN_VALUE));

        assertEquals(Double.MAX_VALUE, scaled.doubleValue());
    }

    @Test
    void memberBarsPreserveSubnormalScaledWicks() {
        final BarSeries source = new BaseBarSeriesBuilder().withName("subnormal-wick")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        final Num close = DoubleNum.valueOf(Double.MAX_VALUE);
        source.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(start.plus(Duration.ofDays(1)))
                .openPrice(close)
                .highPrice(close)
                .lowPrice(close)
                .closePrice(close)
                .volume(1)
                .amount(close)
                .trades(1)
                .add();
        source.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(start.plus(Duration.ofDays(2)))
                .openPrice(close)
                .highPrice(close)
                .lowPrice(Double.MIN_VALUE)
                .closePrice(close)
                .volume(1)
                .amount(close)
                .trades(1)
                .add();

        final BarSeries member = BlockBootstrapNulls.generate(source, 1, 1, 19L).get(0);
        assertEquals(Double.MIN_VALUE, member.getBar(1).getLowPrice().doubleValue());
    }

    @Test
    void memberBarsKeepHighAndLowAroundScaledOpenAndClose() {
        final BarSeries source = new BaseBarSeriesBuilder().withName("scaled-ohlc-bounds")
                .withNumFactory(DoubleNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        final Num sourceClose = DoubleNum.valueOf(Double.MIN_VALUE);
        final Num scaledOpen = DoubleNum.valueOf(2d * Double.MIN_VALUE);
        source.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(start.plus(Duration.ofDays(1)))
                .openPrice(sourceClose)
                .highPrice(sourceClose)
                .lowPrice(sourceClose)
                .closePrice(sourceClose)
                .volume(1)
                .amount(sourceClose)
                .trades(1)
                .add();
        source.barBuilder()
                .timePeriod(Duration.ofDays(1))
                .endTime(start.plus(Duration.ofDays(2)))
                .openPrice(scaledOpen)
                .highPrice(Double.MAX_VALUE)
                .lowPrice(sourceClose)
                .closePrice(sourceClose)
                .volume(1)
                .amount(sourceClose)
                .trades(1)
                .add();

        final BarSeries member = BlockBootstrapNulls.generateMember(source,
                new double[] { -Math.log(Double.MIN_VALUE) }, 1, 23L, 0);
        final Bar bar = member.getBar(1);
        assertEquals(2d, bar.getOpenPrice().doubleValue());
        assertEquals(2d, bar.getHighPrice().doubleValue());
        assertEquals(1d, bar.getLowPrice().doubleValue());
        assertEquals(1d, bar.getClosePrice().doubleValue());
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

    @Test
    void bootstrapMemberShapeTravelsWithSampledReturns() {
        final BarSeries source = buildWickSeries();
        final List<BarSeries> members = BlockBootstrapNulls.generate(source, 3, 1, 7L);
        final BarSeries member = members.get(0);

        // Regression: intrabar shape used to stay in original chronology, so
        // every member inherited the real series' wick sequence. Each member
        // bar must carry the OHLC ratios of the source bar whose close-to-close
        // return was drawn for that position.
        final double[] sourceReturns = new double[source.getBarCount() - 1];
        for (int offset = 1; offset < source.getBarCount(); offset++) {
            sourceReturns[offset - 1] = Math.log(source.getBar(offset).getClosePrice().doubleValue()
                    / source.getBar(offset - 1).getClosePrice().doubleValue());
        }
        boolean sawRelocatedShape = false;
        for (int offset = 1; offset < member.getBarCount(); offset++) {
            final double drawnReturn = Math.log(member.getBar(offset).getClosePrice().doubleValue()
                    / member.getBar(offset - 1).getClosePrice().doubleValue());
            int shapePosition = -1;
            for (int candidate = 0; candidate < sourceReturns.length; candidate++) {
                if (Math.abs(sourceReturns[candidate] - drawnReturn) < 1e-12) {
                    shapePosition = candidate + 1;
                    break;
                }
            }
            assertTrue(shapePosition >= 0, "member return not drawn from the observed tape at offset " + offset);
            final double expectedRatio = source.getBar(shapePosition).getHighPrice().doubleValue()
                    / source.getBar(shapePosition).getClosePrice().doubleValue();
            final double actualRatio = member.getBar(offset).getHighPrice().doubleValue()
                    / member.getBar(offset).getClosePrice().doubleValue();
            assertEquals(expectedRatio, actualRatio, 1e-9, "wick ratio not traveling with sampled return");
            final double chronologicalRatio = source.getBar(offset).getHighPrice().doubleValue()
                    / source.getBar(offset).getClosePrice().doubleValue();
            if (Math.abs(chronologicalRatio - actualRatio) > 1e-9) {
                sawRelocatedShape = true;
            }
        }
        assertTrue(sawRelocatedShape, "sampling never relocated a wick shape; test lost discriminating power");
    }

    @Test
    void logReturnsKeepTinyHighPrecisionMoves() {
        // 1e30 -> 1e30+1 is a real move in DecimalNum space, but its ratio
        // narrows to exactly 1.0 as a double; computing the relative delta in
        // Num first keeps the 1e-30 log return alive instead of recording zero.
        final String[] closes = { "1e30", "1000000000000000000000000000001", "1e30",
                "1000000000000000000000000000001" };
        final BarSeries source = new BaseBarSeriesBuilder().withName("tiny-decimal")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < closes.length; index++) {
            final Num close = DecimalNum.valueOf(closes[index]);
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

        final double[] returns = BlockBootstrapNulls.logReturns(source);
        assertEquals(3, returns.length);
        assertEquals(1e-30d, returns[0], 1e-45d);
        assertEquals(-1e-30d, returns[1], 1e-45d);
        assertEquals(1e-30d, returns[2], 1e-45d);
    }

    @Test
    void logReturnsSurviveRatiosBeyondDoubleRange() {
        // A single-bar jump from 1 to 1e400 has no finite double ratio; the
        // magnitude decomposition must still yield +-ln(1e400) instead of
        // +-Infinity, and reconstruction must stay representable in Num.
        final String[] closes = { "1", "1e400", "1" };
        final BarSeries source = new BaseBarSeriesBuilder().withName("beyond-double")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < closes.length; index++) {
            final Num close = DecimalNum.valueOf(closes[index]);
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

        final double[] returns = BlockBootstrapNulls.logReturns(source);
        final double expected = 400 * Math.log(10);
        assertEquals(expected, returns[0], 1e-9d);
        assertEquals(-expected, returns[1], 1e-9d);

        final BarSeries member = BlockBootstrapNulls.generate(source, 2, 1, 7L).get(0);
        final Num jump = member.getBar(1).getClosePrice().dividedBy(member.getBar(0).getClosePrice());
        assertTrue(jump.isPositive());
    }

    @Test
    void logReturnsTerminateWhenDoubleNumRatioOverflows() {
        // MIN_VALUE -> MAX_VALUE overflows already in the DoubleNum ratio; the
        // difference of decomposed close logs must terminate with a finite
        // value instead of scaling an infinite Num forever.
        final BarSeries source = new BaseBarSeriesBuilder().withName("double-overflow")
                .withNumFactory(org.ta4j.core.num.DoubleNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        final double[] closes = { Double.MIN_VALUE, Double.MAX_VALUE, Double.MIN_VALUE };
        for (int index = 0; index < closes.length; index++) {
            final Num close = org.ta4j.core.num.DoubleNum.valueOf(closes[index]);
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

        final double[] returns = BlockBootstrapNulls.logReturns(source);
        final double expected = Math.log(Double.MAX_VALUE) - Math.log(Double.MIN_VALUE);
        assertEquals(expected, returns[0], 1e-6d);
        assertEquals(-expected, returns[1], 1e-6d);
    }

    @Test
    void bootstrapStaysInNumDomainForHugeDecimalPrices() {
        // Closes beyond double range: the return ratio stays finite in Num
        // domain, while double narrowing used to produce Infinity and abort
        // generation with a positivity failure.
        final String[] closes = { "1e400", "2e400", "1e400", "2e400", "1e400", "2e400", "1e400", "2e400" };
        final BarSeries source = new BaseBarSeriesBuilder().withName("huge-decimal")
                .withNumFactory(DecimalNumFactory.getInstance())
                .build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < closes.length; index++) {
            final Num close = DecimalNum.valueOf(closes[index]);
            final Num open = close.multipliedBy(DecimalNum.valueOf("0.99"));
            source.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(open)
                    .highPrice(close)
                    .lowPrice(open)
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }

        final BarSeries member = BlockBootstrapNulls.generate(source, 3, 1, 7L).get(0);
        assertEquals(source.getBarCount(), member.getBarCount());
        for (int offset = 1; offset < member.getBarCount(); offset++) {
            final double ratio = member.getBar(offset)
                    .getClosePrice()
                    .dividedBy(member.getBar(offset - 1).getClosePrice())
                    .doubleValue();
            assertTrue(ratio == 2.0d || ratio == 0.5d, "unexpected member ratio " + ratio);
        }
    }

    /** Synthetic wick tape shared by shape-travel assertions. */
    private static BarSeries buildWickSeries() {
        final double[] closes = { 100, 101.5, 99.2, 104.1, 102.3, 107.8, 105.2, 110.9, 108.4, 113.6, 111.1, 116.9 };
        final BarSeries series = new BaseBarSeriesBuilder().withName("synthetic-wicks").build();
        final Instant start = Instant.parse("2018-01-01T00:00:00Z");
        for (int index = 0; index < closes.length; index++) {
            final double close = closes[index];
            final double high = close * (1 + (index % 5 + 1) / 50.0);
            final double low = close * (1 - (index % 3 + 1) / 60.0);
            series.barBuilder()
                    .timePeriod(Duration.ofDays(1))
                    .endTime(start.plus(Duration.ofDays(index + 1)))
                    .openPrice(low + (high - low) / 3)
                    .highPrice(high)
                    .lowPrice(low)
                    .closePrice(close)
                    .volume(1)
                    .amount(close)
                    .trades(1)
                    .add();
        }
        return series;
    }
}
