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

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.Assert.*;

public class HourOfDayStrategyTest {

    private BarSeries series;

    @Before
    public void setUp() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // Create bars for different hours of the day
        // Use the same day (2019-09-16) but different hours
        for (int hour = 0; hour < 24; hour++) {
            Instant beginTime = Instant.parse("2019-09-16T" + String.format("%02d:00:00Z", hour));
            Instant endTime = Instant.parse("2019-09-16T" + String.format("%02d:59:59Z", hour));
            series.barBuilder()
                    .timePeriod(Duration.between(beginTime, endTime))
                    .beginTime(beginTime)
                    .endTime(endTime)
                    .closePrice(100d + hour)
                    .add();
        }
    }

    @Test
    public void testConstructorWithHours() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, 9, 17);

        assertNotNull(strategy);
        assertEquals("HourOfDayStrategy_9_17", strategy.getName());
        assertEquals("{\"type\":\"NamedStrategy\",\"label\":\"HourOfDayStrategy_9_17\"}", strategy.toJson());
        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testConstructorWithStringParams() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, "9", "17");

        assertNotNull(strategy);
        assertEquals("HourOfDayStrategy_9_17", strategy.getName());
    }

    @Test
    public void testConstructorWithStringParamsAllHours() {
        for (int entryHour = 0; entryHour < 24; entryHour++) {
            for (int exitHour = 0; exitHour < 24; exitHour++) {
                if (entryHour != exitHour) {
                    HourOfDayStrategy strategy = new HourOfDayStrategy(series, String.valueOf(entryHour),
                            String.valueOf(exitHour));
                    assertNotNull(strategy);
                    assertEquals("HourOfDayStrategy_" + entryHour + "_" + exitHour, strategy.getName());
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullParams() {
        new HourOfDayStrategy(series, (String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyParams() {
        new HourOfDayStrategy(series);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInsufficientParams() {
        new HourOfDayStrategy(series, "9");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidEntryHourNegative() {
        new HourOfDayStrategy(series, "-1", "17");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidEntryHourTooLarge() {
        new HourOfDayStrategy(series, "24", "17");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidExitHourNegative() {
        new HourOfDayStrategy(series, "9", "-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidExitHourTooLarge() {
        new HourOfDayStrategy(series, "9", "24");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithSameEntryAndExitHour() {
        new HourOfDayStrategy(series, 12, 12);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithSameEntryAndExitHourString() {
        new HourOfDayStrategy(series, "12", "12");
    }

    @Test
    public void testConstructorWithNonNumericEntryHour() {
        try {
            new HourOfDayStrategy(series, "abc", "17");
            fail("Expected IllegalArgumentException for non-numeric entry hour");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry hour value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-23"));
        }
    }

    @Test
    public void testConstructorWithNonNumericExitHour() {
        try {
            new HourOfDayStrategy(series, "9", "xyz");
            fail("Expected IllegalArgumentException for non-numeric exit hour");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit hour value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-23"));
        }
    }

    @Test
    public void testEntryRuleSatisfiedOnCorrectHour() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, 9, 17);
        TradingRecord tradingRecord = new BaseTradingRecord();

        // Index 9 is hour 9 - entry rule should be satisfied
        assertTrue(strategy.getEntryRule().isSatisfied(9, tradingRecord));

        // Index 10 is hour 10 - entry rule should not be satisfied
        assertFalse(strategy.getEntryRule().isSatisfied(10, tradingRecord));

        // Index 17 is hour 17 - entry rule should not be satisfied (it's exit hour)
        assertFalse(strategy.getEntryRule().isSatisfied(17, tradingRecord));
    }

    @Test
    public void testExitRuleSatisfiedOnCorrectHour() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, 9, 17);
        TradingRecord tradingRecord = new BaseTradingRecord();

        // Index 9 is hour 9 - exit rule should not be satisfied
        assertFalse(strategy.getExitRule().isSatisfied(9, tradingRecord));

        // Index 17 is hour 17 - exit rule should be satisfied
        assertTrue(strategy.getExitRule().isSatisfied(17, tradingRecord));

        // Index 10 is hour 10 - exit rule should not be satisfied
        assertFalse(strategy.getExitRule().isSatisfied(10, tradingRecord));
    }

    @Test
    public void testBuildAllStrategyPermutations() {
        List<Strategy> strategies = HourOfDayStrategy.buildAllStrategyPermutations(series);

        assertNotNull(strategies);
        // Should have 24 * 23 = 552 strategies (all combinations except where entry ==
        // exit)
        assertEquals(552, strategies.size());

        // Verify all strategies have unique names
        long uniqueNames = strategies.stream().map(Strategy::getName).distinct().count();
        assertEquals(552, uniqueNames);

        // Verify all strategies are HourOfDayStrategy instances
        for (Strategy strategy : strategies) {
            assertTrue(strategy instanceof HourOfDayStrategy);
            assertNotNull(strategy.getName());
            assertTrue(strategy.getName().startsWith("HourOfDayStrategy_"));
        }
    }

    @Test
    public void testBuildAllStrategyPermutationsNoDuplicateEntryExit() {
        List<Strategy> strategies = HourOfDayStrategy.buildAllStrategyPermutations(series);

        // Verify no strategy has the same entry and exit hour
        for (Strategy strategy : strategies) {
            String name = strategy.getName();
            String[] parts = name.split("_");
            assertNotEquals("Strategy should not have same entry and exit hour: " + name, parts[1], parts[2]);
        }
    }

    @Test
    public void testStrategyNameFormat() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, 10, 15);
        String name = strategy.getName();

        assertTrue(name.startsWith("HourOfDayStrategy_"));
        assertTrue(name.contains("10"));
        assertTrue(name.contains("15"));
        assertEquals("HourOfDayStrategy_10_15", name);
    }

    @Test
    public void testStrategyWithBoundaryHours() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, 0, 23);

        assertNotNull(strategy);
        assertEquals("HourOfDayStrategy_0_23", strategy.getName());

        TradingRecord tradingRecord = new BaseTradingRecord();
        // Index 0 is hour 0
        assertTrue(strategy.getEntryRule().isSatisfied(0, tradingRecord));
        // Index 23 is hour 23
        assertTrue(strategy.getExitRule().isSatisfied(23, tradingRecord));
    }

    @Test
    public void testStrategyWithAllHourCombinations() {
        for (int entryHour = 0; entryHour < 24; entryHour++) {
            for (int exitHour = 0; exitHour < 24; exitHour++) {
                if (entryHour != exitHour) {
                    HourOfDayStrategy strategy = new HourOfDayStrategy(series, entryHour, exitHour);
                    assertNotNull(strategy);
                    assertNotNull(strategy.getEntryRule());
                    assertNotNull(strategy.getExitRule());
                }
            }
        }
    }

    @Test
    public void testStrategyRulesAreNotNull() {
        HourOfDayStrategy strategy = new HourOfDayStrategy(series, 12, 18);

        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testStrategyWithNullSeries() {
        try {
            new HourOfDayStrategy(null, 9, 17);
            fail("Expected NullPointerException or IllegalArgumentException for null series");
        } catch (Exception e) {
            // Expected - either NullPointerException or IllegalArgumentException
            assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testParseEntryHourErrorMessage() {
        try {
            new HourOfDayStrategy(series, "INVALID", "17");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry hour value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-23"));
        }
    }

    @Test
    public void testParseExitHourErrorMessage() {
        try {
            new HourOfDayStrategy(series, "9", "INVALID");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit hour value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-23"));
        }
    }

    @Test
    public void testInsufficientParamsErrorMessage() {
        try {
            new HourOfDayStrategy(series, "9");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("At least 2 parameters required"));
        }
    }

    @Test
    public void testEntryHourOutOfRangeErrorMessage() {
        try {
            new HourOfDayStrategy(series, "25", "17");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry hour value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-23"));
        }
    }

    @Test
    public void testExitHourOutOfRangeErrorMessage() {
        try {
            new HourOfDayStrategy(series, "9", "25");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit hour value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-23"));
        }
    }
}
