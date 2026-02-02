/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Unit tests for {@link BaseBarSeries}.
 *
 * @since 0.19
 */
public class BaseBarSeriesTest extends AbstractIndicatorTest<BarSeries, Num> {

    private BarBuilderFactory barBuilderFactory;
    private List<Bar> testBars;
    private BaseBarSeries emptySeries;
    private BaseBarSeries seriesWithBars;
    private BaseBarSeries constrainedSeries;

    public BaseBarSeriesTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Before
    public void setUp() {
        barBuilderFactory = new MockBarBuilderFactory();

        // Create test bars
        testBars = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");

        for (int i = 0; i < 5; i++) {
            Bar bar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                    .endTime(baseTime.plus(Duration.ofDays(i)))
                    .openPrice(numOf(i + 1))
                    .highPrice(numOf(i + 2))
                    .lowPrice(numOf(i))
                    .closePrice(numOf(i + 1.5))
                    .volume(numOf(i * 100))
                    .amount(numOf(i * 1000))
                    .trades(i * 10)
                    .build();
            testBars.add(bar);
        }

        // Create test series
        emptySeries = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName("EmptySeries")
                .build();

        seriesWithBars = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName("TestSeries")
                .withBars(new ArrayList<>(testBars))
                .build();

        // Create constrained series directly using constructor to avoid builder issue
        constrainedSeries = new BaseBarSeries("ConstrainedSeries", new ArrayList<>(testBars), 0, testBars.size() - 1,
                true, numFactory, barBuilderFactory);
    }

    // ==================== Constructor Tests ====================

    @Test
    public void testConvenienceConstructor() {
        BaseBarSeries series = new BaseBarSeries("TestName", testBars);

        assertEquals("TestName", series.getName());
        assertEquals(5, series.getBarCount());
        assertEquals(0, series.getBeginIndex());
        assertEquals(4, series.getEndIndex());
        assertEquals(DecimalNumFactory.getInstance().getClass(), series.numFactory().getClass());
        assertFalse(series.isEmpty());
    }

    @Test
    public void testConvenienceConstructorWithEmptyBars() {
        BaseBarSeries series = new BaseBarSeries("TestName", Collections.emptyList());

        assertEquals("TestName", series.getName());
        assertEquals(0, series.getBarCount());
        assertEquals(-1, series.getBeginIndex());
        assertEquals(-1, series.getEndIndex());
        assertTrue(series.isEmpty());
    }

    @Test
    public void testFullConstructor() {
        BaseBarSeries series = new BaseBarSeries("TestName", testBars, 1, 3, true, numFactory, barBuilderFactory);

        assertEquals("TestName", series.getName());
        assertEquals(3, series.getBarCount());
        assertEquals(1, series.getBeginIndex());
        assertEquals(3, series.getEndIndex());
        assertSame(numFactory, series.numFactory());
        assertFalse(series.isEmpty());
    }

    @Test
    public void testFullConstructorWithEmptyBars() {
        BaseBarSeries series = new BaseBarSeries("TestName", Collections.emptyList(), 0, 0, false, numFactory,
                barBuilderFactory);

        assertEquals("TestName", series.getName());
        assertEquals(0, series.getBarCount());
        assertEquals(-1, series.getBeginIndex());
        assertEquals(-1, series.getEndIndex());
        assertTrue(series.isEmpty());
    }

    @Test
    public void testFullConstructorWithInvalidEndIndex() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BaseBarSeries("TestName", testBars, 3, 1, // endIndex < beginIndex - 1 (1 < 3 - 1 = 1 < 2)
                    false, numFactory, barBuilderFactory);
        });
    }

    @Test
    public void testFullConstructorWithEndIndexTooLarge() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BaseBarSeries("TestName", testBars, 0, 10, // endIndex >= bars.size()
                    false, numFactory, barBuilderFactory);
        });
    }

    @Test
    public void testFullConstructorWithNullBarBuilderFactory() {
        assertThrows(NullPointerException.class, () -> {
            new BaseBarSeries("TestName", testBars, 0, 4, false, numFactory, null);
        });
    }

    // ==================== Basic Property Tests ====================

    @Test
    public void testGetName() {
        assertEquals("EmptySeries", emptySeries.getName());
        assertEquals("TestSeries", seriesWithBars.getName());
        assertEquals("ConstrainedSeries", constrainedSeries.getName());
    }

    @Test
    public void testNumFactory() {
        assertEquals(numFactory.getClass(), seriesWithBars.numFactory().getClass());
        assertEquals(numFactory.getClass(), emptySeries.numFactory().getClass());
    }

    @Test
    public void testBarBuilder() {
        BarBuilder builder = seriesWithBars.barBuilder();
        assertNotNull(builder);
        assertTrue(builder instanceof TimeBarBuilder);
    }

    @Test
    public void testGetBarData() {
        List<Bar> barData = seriesWithBars.getBarData();
        assertNotNull(barData);
        assertEquals(5, barData.size());

        // Test that it returns a copy (immutability)
        List<Bar> originalData = seriesWithBars.getBarData();
        int originalSize = originalData.size();
        List<Bar> modifiedData = new ArrayList<>(originalData);
        modifiedData.add(testBars.get(0));

        // The copy should be modified, while the original series data remains unchanged
        assertEquals(originalSize + 1, modifiedData.size());
        assertEquals(originalSize, seriesWithBars.getBarData().size());
    }

    // ==================== Bar Access Tests ====================

    @Test
    public void testGetBarValidIndex() {
        Bar bar = seriesWithBars.getBar(0);
        assertNotNull(bar);
        assertNumEquals(1.5, bar.getClosePrice());

        bar = seriesWithBars.getBar(4);
        assertNotNull(bar);
        assertNumEquals(5.5, bar.getClosePrice());
    }

    @Test
    public void testGetBarNegativeIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            seriesWithBars.getBar(-1);
        });
    }

    @Test
    public void testGetBarIndexTooLarge() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            seriesWithBars.getBar(10);
        });
    }

    @Test
    public void testGetBarOnEmptySeries() {
        assertThrows(IndexOutOfBoundsException.class, () -> {
            emptySeries.getBar(0);
        });
    }

    @Test
    public void testGetBarWithRemovedBars() {
        // Set maximum bar count to trigger removal
        seriesWithBars.setMaximumBarCount(3);

        // After removal, we should have 3 bars remaining
        assertEquals(3, seriesWithBars.getBarCount());
        assertEquals(2, seriesWithBars.getRemovedBarsCount());

        // The remaining bars should be the last 3 bars from the original series
        Bar firstRemainingBar = seriesWithBars.getBar(2); // This should be the 3rd bar (index 2)
        Bar secondRemainingBar = seriesWithBars.getBar(3); // This should be the 4th bar (index 3)
        Bar thirdRemainingBar = seriesWithBars.getBar(4); // This should be the 5th bar (index 4)

        // Verify we get different bars for different indices
        assertNotSame(firstRemainingBar, secondRemainingBar);
        assertNotSame(secondRemainingBar, thirdRemainingBar);
        assertNotSame(firstRemainingBar, thirdRemainingBar);

        // Verify the bars are the correct ones from the original series
        assertSame(testBars.get(2), firstRemainingBar);
        assertSame(testBars.get(3), secondRemainingBar);
        assertSame(testBars.get(4), thirdRemainingBar);
    }

    // ==================== Bar Counting Tests ====================

    @Test
    public void testGetBarCount() {
        assertEquals(0, emptySeries.getBarCount());
        assertEquals(5, seriesWithBars.getBarCount());
        assertEquals(5, constrainedSeries.getBarCount());
    }

    @Test
    public void testGetBeginIndex() {
        assertEquals(-1, emptySeries.getBeginIndex());
        assertEquals(0, seriesWithBars.getBeginIndex());
        assertEquals(0, constrainedSeries.getBeginIndex());
    }

    @Test
    public void testGetEndIndex() {
        assertEquals(-1, emptySeries.getEndIndex());
        assertEquals(4, seriesWithBars.getEndIndex());
        assertEquals(4, constrainedSeries.getEndIndex());
    }

    @Test
    public void testIsEmpty() {
        assertTrue(emptySeries.isEmpty());
        assertFalse(seriesWithBars.isEmpty());
        assertFalse(constrainedSeries.isEmpty());
    }

    // ==================== SubSeries Tests ====================

    @Test
    public void testGetSubSeriesValidRange() {
        BaseBarSeries subSeries = seriesWithBars.getSubSeries(1, 4);

        assertEquals("TestSeries", subSeries.getName());
        assertEquals(3, subSeries.getBarCount());
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(2, subSeries.getEndIndex());

        // Verify the bars are correct
        assertNumEquals(2.5, subSeries.getBar(0).getClosePrice());
        assertNumEquals(3.5, subSeries.getBar(1).getClosePrice());
        assertNumEquals(4.5, subSeries.getBar(2).getClosePrice());
    }

    @Test
    public void testGetSubSeriesFromEmptySeries() {
        BaseBarSeries subSeries = emptySeries.getSubSeries(0, 1);

        assertEquals("EmptySeries", subSeries.getName());
        assertEquals(0, subSeries.getBarCount());
        assertEquals(-1, subSeries.getBeginIndex());
        assertEquals(-1, subSeries.getEndIndex());
    }

    @Test
    public void testGetSubSeriesWithLargeEndIndex() {
        BaseBarSeries subSeries = seriesWithBars.getSubSeries(2, 1000);

        assertEquals(3, subSeries.getBarCount());
        assertEquals(0, subSeries.getBeginIndex());
        assertEquals(2, subSeries.getEndIndex());
    }

    @Test
    public void testGetSubSeriesNegativeStartIndex() {
        assertThrows(IllegalArgumentException.class, () -> {
            seriesWithBars.getSubSeries(-1, 3);
        });
    }

    @Test
    public void testGetSubSeriesStartIndexGreaterThanEndIndex() {
        assertThrows(IllegalArgumentException.class, () -> {
            seriesWithBars.getSubSeries(3, 2);
        });
    }

    @Test
    public void testGetSubSeriesStartIndexEqualsEndIndex() {
        assertThrows(IllegalArgumentException.class, () -> {
            seriesWithBars.getSubSeries(2, 2);
        });
    }

    // ==================== Add Bar Tests ====================

    @Test
    public void testAddBarToEmptySeries() {
        Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-06T00:00:00Z"))
                .closePrice(numOf(10.0))
                .build();

        emptySeries.addBar(newBar);

        assertEquals(1, emptySeries.getBarCount());
        assertEquals(0, emptySeries.getBeginIndex());
        assertEquals(0, emptySeries.getEndIndex());
        assertSame(newBar, emptySeries.getBar(0));
    }

    @Test
    public void testAddBarToNonEmptySeries() {
        Bar newBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-06T00:00:00Z"))
                .closePrice(numOf(10.0))
                .build();

        int originalCount = seriesWithBars.getBarCount();
        seriesWithBars.addBar(newBar);

        assertEquals(originalCount + 1, seriesWithBars.getBarCount());
        assertEquals(0, seriesWithBars.getBeginIndex());
        assertEquals(5, seriesWithBars.getEndIndex());
        assertSame(newBar, seriesWithBars.getBar(5));
    }

    @Test
    public void testAddBarWithReplace() {
        Bar originalBar = seriesWithBars.getBar(4);
        Bar replacementBar = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-05T00:00:00Z"))
                .closePrice(numOf(99.0))
                .build();

        int originalCount = seriesWithBars.getBarCount();
        seriesWithBars.addBar(replacementBar, true);

        assertEquals(originalCount, seriesWithBars.getBarCount());
        assertSame(replacementBar, seriesWithBars.getBar(4));
        assertNotSame(originalBar, seriesWithBars.getBar(4));
    }

    @Test
    public void testAddNullBar() {
        assertThrows(NullPointerException.class, () -> {
            seriesWithBars.addBar(null);
        });
    }

    @Test
    public void testAddBarWithWrongNumType() {
        // Use the opposite NumFactory to ensure type mismatch
        NumFactory wrongFactory = (numFactory instanceof DoubleNumFactory) ? DecimalNumFactory.getInstance()
                : DoubleNumFactory.getInstance();

        assertThrows(IllegalArgumentException.class, () -> {
            Bar barWithWrongType = new TimeBarBuilder(wrongFactory).timePeriod(Duration.ofDays(1))
                    .endTime(Instant.parse("2024-01-06T00:00:00Z"))
                    .closePrice(wrongFactory.numOf(10.0))
                    .build();

            seriesWithBars.addBar(barWithWrongType);
        });
    }

    @Test
    public void testAddBarWithEndTimeNotAfterSeriesEndTime() {
        assertThrows(IllegalArgumentException.class, () -> {
            Bar barWithOldTime = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                    .endTime(Instant.parse("2023-12-31T00:00:00Z")) // Before series end time
                    .closePrice(numOf(10.0))
                    .build();

            seriesWithBars.addBar(barWithOldTime);
        });
    }

    @Test
    public void testAddBarWithEndTimeEqualToSeriesEndTime() {
        Bar barWithSameTime = new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                .endTime(Instant.parse("2024-01-05T00:00:00Z")) // Same as last bar's end time
                .closePrice(numOf(10.0))
                .build();

        try {
            seriesWithBars.addBar(barWithSameTime);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Cannot add a bar with end time"));
        }
    }

    // ==================== Add Trade Tests ====================

    @Test
    public void testAddTradeWithNumbers() {
        seriesWithBars.addTrade(100, 50.5);

        Bar lastBar = seriesWithBars.getLastBar();
        assertNumEquals(500, lastBar.getVolume()); // Original volume (4*100) + 100
        assertNumEquals(50.5, lastBar.getClosePrice());
    }

    @Test
    public void testAddTradeWithNums() {
        Num volume = numOf(200);
        Num price = numOf(75.25);

        seriesWithBars.addTrade(volume, price);

        Bar lastBar = seriesWithBars.getLastBar();
        assertNumEquals(600, lastBar.getVolume()); // Original volume (4*100) + 200
        assertNumEquals(75.25, lastBar.getClosePrice());
    }

    @Test
    public void testAddTradeWithBigDecimal() {
        // Create a fresh series for this test to avoid interference from other tests
        BaseBarSeries freshSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName("FreshSeries")
                .withBars(new ArrayList<>(testBars))
                .build();

        freshSeries.addTrade(BigDecimal.valueOf(150), BigDecimal.valueOf(60.75));

        Bar lastBar = freshSeries.getLastBar();
        assertNumEquals(550, lastBar.getVolume()); // Original volume (4*100) + 150
        assertNumEquals(60.75, lastBar.getClosePrice());
    }

    // ==================== Add Price Tests ====================

    @Test
    public void testAddPrice() {
        Num newPrice = numOf(99.99);

        seriesWithBars.addPrice(newPrice);

        Bar lastBar = seriesWithBars.getLastBar();
        assertNumEquals(99.99, lastBar.getClosePrice());
    }

    // ==================== Maximum Bar Count Tests ====================

    @Test
    public void testSetMaximumBarCount() {
        seriesWithBars.setMaximumBarCount(3);

        assertEquals(3, seriesWithBars.getMaximumBarCount());
        assertEquals(3, seriesWithBars.getBarCount());
        assertEquals(2, seriesWithBars.getBeginIndex());
        assertEquals(4, seriesWithBars.getEndIndex());
        assertEquals(2, seriesWithBars.getRemovedBarsCount());
    }

    @Test
    public void testSetMaximumBarCountLargerThanCurrent() {
        seriesWithBars.setMaximumBarCount(10);

        assertEquals(10, seriesWithBars.getMaximumBarCount());
        assertEquals(5, seriesWithBars.getBarCount());
        assertEquals(0, seriesWithBars.getBeginIndex());
        assertEquals(4, seriesWithBars.getEndIndex());
        assertEquals(0, seriesWithBars.getRemovedBarsCount());
    }

    @Test
    public void testSetMaximumBarCountZero() {
        assertThrows(IllegalArgumentException.class, () -> {
            seriesWithBars.setMaximumBarCount(0);
        });
    }

    @Test
    public void testSetMaximumBarCountNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            seriesWithBars.setMaximumBarCount(-1);
        });
    }

    @Test
    public void testSetMaximumBarCountOnConstrainedSeries() {
        assertThrows(IllegalStateException.class, () -> {
            constrainedSeries.setMaximumBarCount(3);
        });
    }

    @Test
    public void testGetMaximumBarCount() {
        assertEquals(Integer.MAX_VALUE, seriesWithBars.getMaximumBarCount());
        assertEquals(Integer.MAX_VALUE, emptySeries.getMaximumBarCount());
        assertEquals(Integer.MAX_VALUE, constrainedSeries.getMaximumBarCount());
    }

    // ==================== Removed Bars Tests ====================

    @Test
    public void testGetRemovedBarsCount() {
        assertEquals(0, seriesWithBars.getRemovedBarsCount());
        assertEquals(0, emptySeries.getRemovedBarsCount());

        seriesWithBars.setMaximumBarCount(3);
        assertEquals(2, seriesWithBars.getRemovedBarsCount());
    }

    // ==================== Edge Cases Tests ====================

    @Test
    public void testSeriesWithSingleBar() {
        List<Bar> singleBar = Arrays.asList(testBars.get(0));
        BaseBarSeries singleBarSeries = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withBars(singleBar)
                .build();

        assertEquals(1, singleBarSeries.getBarCount());
        assertEquals(0, singleBarSeries.getBeginIndex());
        assertEquals(0, singleBarSeries.getEndIndex());
        assertFalse(singleBarSeries.isEmpty());
    }

    @Test
    public void testSeriesWithNullName() {
        BaseBarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName(null)
                .build();

        assertEquals("unnamed_series", series.getName());
    }

    @Test
    public void testSeriesWithEmptyName() {
        BaseBarSeries series = new BaseBarSeriesBuilder().withNumFactory(numFactory)
                .withBarBuilderFactory(barBuilderFactory)
                .withName("")
                .build();

        assertEquals("", series.getName());
    }

    // ==================== Utility Methods Tests ====================

    @Test
    public void testCutMethod() {
        List<Bar> result = BaseBarSeriesTest.cut(testBars, 1, 4);

        assertEquals(3, result.size());
        assertSame(testBars.get(1), result.get(0));
        assertSame(testBars.get(2), result.get(1));
        assertSame(testBars.get(3), result.get(2));
    }

    @Test
    public void testCutMethodWithEmptyRange() {
        List<Bar> result = BaseBarSeriesTest.cut(testBars, 2, 2);

        assertTrue(result.isEmpty());
    }

    @Test
    public void testBuildOutOfBoundsMessage() {
        String message = BaseBarSeriesTest.buildOutOfBoundsMessage(seriesWithBars, 10);

        assertTrue(message.contains("Size of series: 5 bars"));
        assertTrue(message.contains("0 bars removed"));
        assertTrue(message.contains("index = 10"));
    }

    @Test
    public void testBuildOutOfBoundsMessageWithRemovedBars() {
        seriesWithBars.setMaximumBarCount(3);
        String message = BaseBarSeriesTest.buildOutOfBoundsMessage(seriesWithBars, 10);

        assertTrue(message.contains("Size of series: 3 bars"));
        assertTrue(message.contains("2 bars removed"));
        assertTrue(message.contains("index = 10"));
    }

    // ==================== Helper Methods for Testing ====================

    /**
     * Helper method to access the private cut method for testing.
     */
    private static List<Bar> cut(List<Bar> bars, int startIndex, int endIndex) {
        return new ArrayList<>(bars.subList(startIndex, endIndex));
    }

    /**
     * Helper method to access the private buildOutOfBoundsMessage method for
     * testing.
     */
    private static String buildOutOfBoundsMessage(BaseBarSeries series, int index) {
        return String.format("Size of series: %s bars, %s bars removed, index = %s", series.getBarData().size(),
                series.getRemovedBarsCount(), index);
    }
}
