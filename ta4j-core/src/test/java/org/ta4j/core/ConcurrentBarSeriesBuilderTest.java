/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.bars.TimeBarBuilderFactory;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Comprehensive unit tests for {@link ConcurrentBarSeriesBuilder}.
 *
 * @since 0.22.2
 */
@RunWith(Parameterized.class)
public class ConcurrentBarSeriesBuilderTest extends AbstractIndicatorTest<BarSeries, Num> {

    public ConcurrentBarSeriesBuilderTest(NumFactory numFactory) {
        super(numFactory);
    }

    // ==================== Constructor and Defaults Tests ====================

    @Test
    public void testDefaultConstructor() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        assertNotNull(builder);
    }

    @Test
    public void testBuildWithDefaults() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().build();

        assertEquals("unnamed_series", series.getName());
        assertEquals(0, series.getBarCount());
        assertEquals(-1, series.getBeginIndex());
        assertEquals(-1, series.getEndIndex());
        assertTrue(series.isEmpty());
        assertEquals(Integer.MAX_VALUE, series.getMaximumBarCount());
        assertTrue(series.numFactory() instanceof DecimalNumFactory);
        assertTrue(series.barBuilderFactory() instanceof TimeBarBuilderFactory);
    }

    // ==================== withName() Tests ====================

    @Test
    public void testWithName() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName("MySeries").build();

        assertEquals("MySeries", series.getName());
    }

    @Test
    public void testWithNameNull() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName(null).build();

        assertEquals("unnamed_series", series.getName());
    }

    @Test
    public void testWithNameEmptyString() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName("").build();

        assertEquals("", series.getName());
    }

    @Test
    public void testWithNameChaining() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        ConcurrentBarSeriesBuilder result = builder.withName("Test");
        assertSame(builder, result);
    }

    // ==================== withBars() Tests ====================

    @Test
    public void testWithBars() {
        List<Bar> bars = createTestBars(3);
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(bars).build();

        assertEquals(3, series.getBarCount());
        assertEquals(0, series.getBeginIndex());
        assertEquals(2, series.getEndIndex());
        assertFalse(series.isEmpty());
    }

    @Test
    public void testWithBarsEmptyList() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(Collections.emptyList()).build();

        assertEquals(0, series.getBarCount());
        assertEquals(-1, series.getBeginIndex());
        assertEquals(-1, series.getEndIndex());
        assertTrue(series.isEmpty());
    }

    @Test
    public void testWithBarsCreatesCopy() {
        List<Bar> originalBars = createTestBars(3);
        List<Bar> barsCopy = new ArrayList<>(originalBars);
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder().withBars(barsCopy);

        // Modify original list
        barsCopy.add(createTestBar(3));

        ConcurrentBarSeries series = builder.build();
        assertEquals(3, series.getBarCount()); // Should still be 3, not 4
    }

    @Test
    public void testWithBarsChaining() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        ConcurrentBarSeriesBuilder result = builder.withBars(Collections.emptyList());
        assertSame(builder, result);
    }

    // ==================== withNumFactory() Tests ====================

    @Test
    public void testWithNumFactory() {
        NumFactory customFactory = numFactory instanceof DoubleNumFactory ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();

        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withNumFactory(customFactory).build();

        assertSame(customFactory, series.numFactory());
    }

    @Test
    public void testWithNumFactoryChaining() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        ConcurrentBarSeriesBuilder result = builder.withNumFactory(numFactory);
        assertSame(builder, result);
    }

    // ==================== withBarBuilderFactory() Tests ====================

    @Test
    public void testWithBarBuilderFactory() {
        BarBuilderFactory customFactory = new MockBarBuilderFactory();

        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBarBuilderFactory(customFactory).build();

        assertSame(customFactory, series.barBuilderFactory());
    }

    @Test
    public void testWithBarBuilderFactoryChaining() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        BarBuilderFactory factory = new MockBarBuilderFactory();
        ConcurrentBarSeriesBuilder result = builder.withBarBuilderFactory(factory);
        assertSame(builder, result);
    }

    // ==================== withMaxBarCount() Tests ====================

    @Test
    public void testWithMaxBarCount() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withMaxBarCount(100).build();

        assertEquals(100, series.getMaximumBarCount());
    }

    @Test
    public void testWithMaxBarCountAtMaxValueKeepsSeriesUnconstrained() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withMaxBarCount(Integer.MAX_VALUE).build();

        series.setMaximumBarCount(100);
        assertEquals(100, series.getMaximumBarCount());
    }

    @Test
    public void testWithMaxBarCountZero() {
        // Maximum bar count must be strictly positive
        assertThrows(IllegalArgumentException.class, () -> new ConcurrentBarSeriesBuilder().withMaxBarCount(0).build());
    }

    @Test
    public void testWithMaxBarCountNegative() {
        // Maximum bar count must be strictly positive
        assertThrows(IllegalArgumentException.class,
                () -> new ConcurrentBarSeriesBuilder().withMaxBarCount(-1).build());
    }

    @Test
    public void testWithMaxBarCountChaining() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        ConcurrentBarSeriesBuilder result = builder.withMaxBarCount(100);
        assertSame(builder, result);
    }

    // ==================== Builder Pattern (Fluent Interface) Tests
    // ====================

    @Test
    public void testFluentInterfaceChaining() {
        List<Bar> bars = createTestBars(2);
        BarBuilderFactory factory = new MockBarBuilderFactory();

        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName("FluentTest")
                .withBars(bars)
                .withNumFactory(numFactory)
                .withBarBuilderFactory(factory)
                .withMaxBarCount(50)
                .build();

        assertEquals("FluentTest", series.getName());
        assertEquals(2, series.getBarCount());
        assertSame(numFactory, series.numFactory());
        assertSame(factory, series.barBuilderFactory());
        assertEquals(50, series.getMaximumBarCount());
        // Constrained state is internal and not exposed via public API
    }

    @Test
    public void testBuildWithoutMaxBarCountIsConstrained() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(createTestBars(1)).build();

        assertThrows(IllegalStateException.class, () -> series.setMaximumBarCount(50));
    }

    @Test
    public void testBuildWithMaxBarCountAllowsUpdates() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(createTestBars(1))
                .withMaxBarCount(50)
                .build();

        series.setMaximumBarCount(75);
        assertEquals(75, series.getMaximumBarCount());
    }

    // ==================== Builder Reset After Build Tests ====================

    @Test
    public void testBuilderResetsAfterBuild() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder().withName("First").withMaxBarCount(100);

        ConcurrentBarSeries first = builder.build();
        assertEquals("First", first.getName());
        assertEquals(100, first.getMaximumBarCount());
        // Constrained state is internal and not exposed via public API

        // Builder should be reset, so second build should use defaults
        ConcurrentBarSeries second = builder.build();
        assertEquals("unnamed_series", second.getName());
        assertEquals(Integer.MAX_VALUE, second.getMaximumBarCount());
        // Constrained state is internal and not exposed via public API
    }

    @Test
    public void testBuilderResetsBarsAfterBuild() {
        List<Bar> bars1 = createTestBars(3);
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder().withBars(bars1);

        ConcurrentBarSeries first = builder.build();
        assertEquals(3, first.getBarCount());

        // Builder should be reset, so second build should be empty
        ConcurrentBarSeries second = builder.build();
        assertEquals(0, second.getBarCount());
    }

    // ==================== Build with All Configurations Tests ====================

    @Test
    public void testBuildWithAllConfigurations() {
        List<Bar> bars = createTestBars(5);
        NumFactory customNumFactory = numFactory instanceof DoubleNumFactory ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        BarBuilderFactory customBarFactory = new MockBarBuilderFactory();

        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName("CompleteTest")
                .withBars(bars)
                .withNumFactory(customNumFactory)
                .withBarBuilderFactory(customBarFactory)
                .withMaxBarCount(200)
                .build();

        assertEquals("CompleteTest", series.getName());
        assertEquals(5, series.getBarCount());
        assertEquals(0, series.getBeginIndex());
        assertEquals(4, series.getEndIndex());
        assertSame(customNumFactory, series.numFactory());
        assertSame(customBarFactory, series.barBuilderFactory());
        assertEquals(200, series.getMaximumBarCount());
        // Constrained state is internal and not exposed via public API
        assertFalse(series.isEmpty());
    }

    @Test
    public void testBuildWithEmptyBarsAndAllConfigurations() {
        NumFactory customNumFactory = numFactory instanceof DoubleNumFactory ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();
        BarBuilderFactory customBarFactory = new MockBarBuilderFactory();

        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withName("EmptyBarsTest")
                .withBars(Collections.emptyList())
                .withNumFactory(customNumFactory)
                .withBarBuilderFactory(customBarFactory)
                .withMaxBarCount(50)
                .build();

        assertEquals("EmptyBarsTest", series.getName());
        assertEquals(0, series.getBarCount());
        assertEquals(-1, series.getBeginIndex());
        assertEquals(-1, series.getEndIndex());
        assertSame(customNumFactory, series.numFactory());
        assertSame(customBarFactory, series.barBuilderFactory());
        assertEquals(50, series.getMaximumBarCount());
        // Constrained state is internal and not exposed via public API
        assertTrue(series.isEmpty());
    }

    // ==================== Multiple Builds Tests ====================

    @Test
    public void testMultipleBuilds() {
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();

        ConcurrentBarSeries series1 = builder.withName("Series1").build();
        ConcurrentBarSeries series2 = builder.withName("Series2").withMaxBarCount(100).build();
        ConcurrentBarSeries series3 = builder.withName("Series3").withBars(createTestBars(2)).build();

        assertEquals("Series1", series1.getName());
        assertEquals("Series2", series2.getName());
        assertEquals(100, series2.getMaximumBarCount());
        assertEquals("Series3", series3.getName());
        assertEquals(2, series3.getBarCount());
    }

    // ==================== Edge Cases Tests ====================

    @Test
    public void testBuildWithSingleBar() {
        List<Bar> singleBar = createTestBars(1);
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(singleBar).build();

        assertEquals(1, series.getBarCount());
        assertEquals(0, series.getBeginIndex());
        assertEquals(0, series.getEndIndex());
    }

    @Test
    public void testBuildWithLargeBarCount() {
        List<Bar> manyBars = createTestBars(1000);
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(manyBars).build();

        assertEquals(1000, series.getBarCount());
        assertEquals(0, series.getBeginIndex());
        assertEquals(999, series.getEndIndex());
    }

    @Test
    public void testBuildWithMaxBarCountApplied() {
        List<Bar> bars = createTestBars(10);
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().withBars(bars).withMaxBarCount(5).build();

        // Max bar count is applied immediately, so only 5 bars should remain
        // When bars are removed, beginIndex is adjusted based on removedBarsCount
        assertEquals(5, series.getBarCount());
        assertEquals(5, series.getMaximumBarCount());
        assertEquals(5, series.getBeginIndex()); // Adjusted after removing 5 bars
        assertEquals(9, series.getEndIndex()); // End index remains at 9 (original last bar)
        assertEquals(5, series.getRemovedBarsCount());
    }

    @Test
    public void testBuildReturnsConcurrentBarSeries() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().build();
        assertTrue(series instanceof ConcurrentBarSeries);
    }

    // ==================== Null Parameter Handling Tests ====================

    @Test(expected = NullPointerException.class)
    public void testWithBarsNull() {
        new ConcurrentBarSeriesBuilder().withBars(null);
    }

    @Test
    public void testWithNumFactoryNull() {
        // Null numFactory should be accepted (will use default or fail at build time)
        // Actually, looking at the implementation, it just assigns null
        // Let's verify it doesn't throw immediately
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        builder.withNumFactory(null);
        // Build will likely fail, but builder accepts null
    }

    @Test
    public void testWithBarBuilderFactoryNull() {
        // Null barBuilderFactory should be accepted (will use default or fail at build
        // time)
        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder();
        builder.withBarBuilderFactory(null);
        // Build will likely fail, but builder accepts null
    }

    // ==================== Builder State Isolation Tests ====================

    @Test
    public void testBuilderStateIsolation() {
        List<Bar> bars1 = createTestBars(3);
        List<Bar> bars2 = createTestBars(5);

        ConcurrentBarSeriesBuilder builder = new ConcurrentBarSeriesBuilder().withName("Test1")
                .withBars(bars1)
                .withMaxBarCount(100);

        ConcurrentBarSeries series1 = builder.build();

        // Modify builder for second series
        builder.withName("Test2").withBars(bars2).withMaxBarCount(200);

        ConcurrentBarSeries series2 = builder.build();

        // First series should be unaffected
        assertEquals("Test1", series1.getName());
        assertEquals(3, series1.getBarCount());
        assertEquals(100, series1.getMaximumBarCount());

        // Second series should have new values
        assertEquals("Test2", series2.getName());
        assertEquals(5, series2.getBarCount());
        assertEquals(200, series2.getMaximumBarCount());
    }

    // ==================== Default BarBuilderFactory Tests ====================

    @Test
    public void testDefaultBarBuilderFactory() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().build();
        BarBuilderFactory factory = series.barBuilderFactory();
        assertNotNull(factory);
        assertTrue(factory instanceof TimeBarBuilderFactory);
    }

    @Test
    public void testDefaultBarBuilderFactoryCreatesRealtimeBars() {
        ConcurrentBarSeries series = new ConcurrentBarSeriesBuilder().build();
        BarBuilderFactory factory = series.barBuilderFactory();

        // Default factory should create realtime bars (TimeBarBuilderFactory(true))
        BarBuilder builder = factory.createBarBuilder(series);
        assertNotNull(builder);
    }

    // ==================== UNNAMED_SERIES_NAME Constant Tests ====================

    @Test
    public void testUnnamedSeriesNameConstant() {
        ConcurrentBarSeries series1 = new ConcurrentBarSeriesBuilder().build();
        ConcurrentBarSeries series2 = new ConcurrentBarSeriesBuilder().withName(null).build();

        assertEquals("unnamed_series", series1.getName());
        assertEquals("unnamed_series", series2.getName());
    }

    // ==================== Helper Methods ====================

    private List<Bar> createTestBars(int count) {
        List<Bar> bars = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < count; i++) {
            bars.add(createTestBar(i, baseTime));
        }
        return bars;
    }

    private Bar createTestBar(int index) {
        return createTestBar(index, Instant.parse("2024-01-01T00:00:00Z"));
    }

    private Bar createTestBar(int index, Instant baseTime) {
        return new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(baseTime.plus(Duration.ofDays(index)))
                .openPrice(numOf(index + 1))
                .highPrice(numOf(index + 2))
                .lowPrice(numOf(index))
                .closePrice(numOf(index + 1.5))
                .volume(numOf(index * 100))
                .amount(numOf(index * 1000))
                .trades(index * 10L)
                .build();
    }
}
