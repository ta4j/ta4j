/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class DayOfWeekStrategyTest {

    private BarSeries series;

    @Before
    public void setUp() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // Create bars for each day of the week starting from Monday
        // 2019-09-16 is a Monday
        // Note: DateTimeIndicator default constructor uses Bar::getBeginTime
        // We set beginTime explicitly to match the day we want to test
        // Use Duration.between to calculate the exact timePeriod
        Instant mondayBegin = Instant.parse("2019-09-16T00:00:00Z");
        Instant mondayEnd = Instant.parse("2019-09-16T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(mondayBegin, mondayEnd))
                .beginTime(mondayBegin)
                .endTime(mondayEnd)
                .closePrice(100d)
                .add(); // Monday
        Instant tuesdayBegin = Instant.parse("2019-09-17T00:00:00Z");
        Instant tuesdayEnd = Instant.parse("2019-09-17T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(tuesdayBegin, tuesdayEnd))
                .beginTime(tuesdayBegin)
                .endTime(tuesdayEnd)
                .closePrice(101d)
                .add(); // Tuesday
        Instant wednesdayBegin = Instant.parse("2019-09-18T00:00:00Z");
        Instant wednesdayEnd = Instant.parse("2019-09-18T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(wednesdayBegin, wednesdayEnd))
                .beginTime(wednesdayBegin)
                .endTime(wednesdayEnd)
                .closePrice(102d)
                .add(); // Wednesday
        Instant thursdayBegin = Instant.parse("2019-09-19T00:00:00Z");
        Instant thursdayEnd = Instant.parse("2019-09-19T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(thursdayBegin, thursdayEnd))
                .beginTime(thursdayBegin)
                .endTime(thursdayEnd)
                .closePrice(103d)
                .add(); // Thursday
        Instant fridayBegin = Instant.parse("2019-09-20T00:00:00Z");
        Instant fridayEnd = Instant.parse("2019-09-20T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(fridayBegin, fridayEnd))
                .beginTime(fridayBegin)
                .endTime(fridayEnd)
                .closePrice(104d)
                .add(); // Friday
        Instant saturdayBegin = Instant.parse("2019-09-21T00:00:00Z");
        Instant saturdayEnd = Instant.parse("2019-09-21T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(saturdayBegin, saturdayEnd))
                .beginTime(saturdayBegin)
                .endTime(saturdayEnd)
                .closePrice(105d)
                .add(); // Saturday
        Instant sundayBegin = Instant.parse("2019-09-22T00:00:00Z");
        Instant sundayEnd = Instant.parse("2019-09-22T23:59:59Z");
        series.barBuilder()
                .timePeriod(Duration.between(sundayBegin, sundayEnd))
                .beginTime(sundayBegin)
                .endTime(sundayEnd)
                .closePrice(106d)
                .add(); // Sunday
    }

    @Test
    public void testConstructorWithDayOfWeek() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, DayOfWeek.MONDAY, DayOfWeek.FRIDAY);

        assertNotNull(strategy);
        assertEquals("DayOfWeekStrategy_MONDAY_FRIDAY", strategy.getName());
        assertEquals("{\"type\":\"NamedStrategy\",\"label\":\"DayOfWeekStrategy_MONDAY_FRIDAY\"}", strategy.toJson());
        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testConstructorWithStringParams() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, "MONDAY", "FRIDAY");

        assertNotNull(strategy);
        assertEquals("DayOfWeekStrategy_MONDAY_FRIDAY", strategy.getName());
    }

    @Test
    public void testConstructorWithStringParamsAllDays() {
        for (DayOfWeek entryDay : DayOfWeek.values()) {
            for (DayOfWeek exitDay : DayOfWeek.values()) {
                if (entryDay != exitDay) {
                    DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, entryDay.name(), exitDay.name());
                    assertNotNull(strategy);
                    assertEquals("DayOfWeekStrategy_" + entryDay + "_" + exitDay, strategy.getName());
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullParams() {
        new DayOfWeekStrategy(series, (String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyParams() {
        new DayOfWeekStrategy(series);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInsufficientParams() {
        new DayOfWeekStrategy(series, "MONDAY");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidEntryDay() {
        new DayOfWeekStrategy(series, "INVALID_DAY", "FRIDAY");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidExitDay() {
        new DayOfWeekStrategy(series, "MONDAY", "INVALID_DAY");
    }

    @Test
    public void testConstructorWithCaseSensitiveDayNames() {
        try {
            new DayOfWeekStrategy(series, "monday", "friday");
            fail("Expected IllegalArgumentException for lowercase day names");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry DayOfWeek value"));
        }
    }

    @Test
    public void testEntryRuleSatisfiedOnCorrectDay() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        TradingRecord tradingRecord = new BaseTradingRecord();

        // Index 0 is Monday - entry rule should be satisfied
        assertTrue(strategy.getEntryRule().isSatisfied(0, tradingRecord));

        // Index 1 is Tuesday - entry rule should not be satisfied
        assertFalse(strategy.getEntryRule().isSatisfied(1, tradingRecord));

        // Index 4 is Friday - entry rule should not be satisfied (it's exit day)
        assertFalse(strategy.getEntryRule().isSatisfied(4, tradingRecord));
    }

    @Test
    public void testExitRuleSatisfiedOnCorrectDay() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
        TradingRecord tradingRecord = new BaseTradingRecord();

        // Index 0 is Monday - exit rule should not be satisfied
        assertFalse(strategy.getExitRule().isSatisfied(0, tradingRecord));

        // Index 4 is Friday - exit rule should be satisfied
        assertTrue(strategy.getExitRule().isSatisfied(4, tradingRecord));

        // Index 1 is Tuesday - exit rule should not be satisfied
        assertFalse(strategy.getExitRule().isSatisfied(1, tradingRecord));
    }

    @Test
    public void testBuildAllStrategyPermutations() {
        List<Strategy> strategies = DayOfWeekStrategy.buildAllStrategyPermutations(series);

        assertNotNull(strategies);
        // Should have 7 * 6 = 42 strategies (all combinations except where entry ==
        // exit)
        assertEquals(42, strategies.size());

        // Verify all strategies have unique names
        long uniqueNames = strategies.stream().map(Strategy::getName).distinct().count();
        assertEquals(42, uniqueNames);

        // Verify all strategies are DayOfWeekStrategy instances
        for (Strategy strategy : strategies) {
            assertTrue(strategy instanceof DayOfWeekStrategy);
            assertNotNull(strategy.getName());
            assertTrue(strategy.getName().startsWith("DayOfWeekStrategy_"));
        }
    }

    @Test
    public void testBuildAllStrategyPermutationsNoDuplicateEntryExit() {
        List<Strategy> strategies = DayOfWeekStrategy.buildAllStrategyPermutations(series);

        // Verify no strategy has the same entry and exit day
        for (Strategy strategy : strategies) {
            String name = strategy.getName();
            String[] parts = name.split("_");
            assertNotEquals("Strategy should not have same entry and exit day: " + name, parts[1], parts[2]);
        }
    }

    @Test
    public void testStrategyNameFormat() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);
        String name = strategy.getName();

        assertTrue(name.startsWith("DayOfWeekStrategy_"));
        assertTrue(name.contains("TUESDAY"));
        assertTrue(name.contains("THURSDAY"));
        assertEquals("DayOfWeekStrategy_TUESDAY_THURSDAY", name);
    }

    @Test
    public void testStrategyWithWeekendDays() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);

        assertNotNull(strategy);
        assertEquals("DayOfWeekStrategy_SATURDAY_SUNDAY", strategy.getName());

        TradingRecord tradingRecord = new BaseTradingRecord();
        // Index 5 is Saturday
        assertTrue(strategy.getEntryRule().isSatisfied(5, tradingRecord));
        // Index 6 is Sunday
        assertTrue(strategy.getExitRule().isSatisfied(6, tradingRecord));
    }

    @Test
    public void testStrategyWithAllDayCombinations() {
        for (DayOfWeek entryDay : DayOfWeek.values()) {
            for (DayOfWeek exitDay : DayOfWeek.values()) {
                if (entryDay != exitDay) {
                    DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, entryDay, exitDay);
                    assertNotNull(strategy);
                    assertNotNull(strategy.getEntryRule());
                    assertNotNull(strategy.getExitRule());
                }
            }
        }
    }

    @Test
    public void testStrategyRulesAreNotNull() {
        DayOfWeekStrategy strategy = new DayOfWeekStrategy(series, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testStrategyWithNullSeries() {
        try {
            new DayOfWeekStrategy(null, DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
            fail("Expected NullPointerException or IllegalArgumentException for null series");
        } catch (Exception e) {
            // Expected - either NullPointerException or IllegalArgumentException
            assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testParseEntryDayOfWeekErrorMessage() {
        try {
            new DayOfWeekStrategy(series, "INVALID", "FRIDAY");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry DayOfWeek value"));
            assertTrue(e.getMessage().contains("Valid values are:"));
        }
    }

    @Test
    public void testParseExitDayOfWeekErrorMessage() {
        try {
            new DayOfWeekStrategy(series, "MONDAY", "INVALID");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit DayOfWeek value"));
            assertTrue(e.getMessage().contains("Valid values are:"));
        }
    }

    @Test
    public void testInsufficientParamsErrorMessage() {
        try {
            new DayOfWeekStrategy(series, "MONDAY");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("At least 2 parameters required"));
        }
    }
}
