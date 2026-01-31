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

public class MinuteOfHourStrategyTest {

    private BarSeries series;

    @Before
    public void setUp() {
        NumFactory numFactory = DecimalNumFactory.getInstance();
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).build();

        // Create bars for different minutes of the hour
        // Use the same hour (12:00) but different minutes
        for (int minute = 0; minute < 60; minute++) {
            Instant beginTime = Instant.parse("2019-09-16T12:" + String.format("%02d:00Z", minute));
            Instant endTime = Instant.parse("2019-09-16T12:" + String.format("%02d:59Z", minute));
            series.barBuilder()
                    .timePeriod(Duration.between(beginTime, endTime))
                    .beginTime(beginTime)
                    .endTime(endTime)
                    .closePrice(100d + minute)
                    .add();
        }
    }

    @Test
    public void testConstructorWithMinutes() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, 15, 45);

        assertNotNull(strategy);
        assertEquals("MinuteOfHourStrategy_15_45", strategy.getName());
        assertEquals("{\"type\":\"NamedStrategy\",\"label\":\"MinuteOfHourStrategy_15_45\"}", strategy.toJson());
        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testConstructorWithStringParams() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, "15", "45");

        assertNotNull(strategy);
        assertEquals("MinuteOfHourStrategy_15_45", strategy.getName());
    }

    @Test
    public void testConstructorWithStringParamsAllMinutes() {
        for (int entryMinute = 0; entryMinute < 60; entryMinute++) {
            for (int exitMinute = 0; exitMinute < 60; exitMinute++) {
                if (entryMinute != exitMinute) {
                    MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, String.valueOf(entryMinute),
                            String.valueOf(exitMinute));
                    assertNotNull(strategy);
                    assertEquals("MinuteOfHourStrategy_" + entryMinute + "_" + exitMinute, strategy.getName());
                }
            }
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullParams() {
        new MinuteOfHourStrategy(series, (String[]) null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithEmptyParams() {
        new MinuteOfHourStrategy(series);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInsufficientParams() {
        new MinuteOfHourStrategy(series, "15");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidEntryMinuteNegative() {
        new MinuteOfHourStrategy(series, "-1", "45");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidEntryMinuteTooLarge() {
        new MinuteOfHourStrategy(series, "60", "45");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidExitMinuteNegative() {
        new MinuteOfHourStrategy(series, "15", "-1");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithInvalidExitMinuteTooLarge() {
        new MinuteOfHourStrategy(series, "15", "60");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithSameEntryAndExitMinute() {
        new MinuteOfHourStrategy(series, 30, 30);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithSameEntryAndExitMinuteString() {
        new MinuteOfHourStrategy(series, "30", "30");
    }

    @Test
    public void testConstructorWithNonNumericEntryMinute() {
        try {
            new MinuteOfHourStrategy(series, "abc", "45");
            fail("Expected IllegalArgumentException for non-numeric entry minute");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry minute value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-59"));
        }
    }

    @Test
    public void testConstructorWithNonNumericExitMinute() {
        try {
            new MinuteOfHourStrategy(series, "15", "xyz");
            fail("Expected IllegalArgumentException for non-numeric exit minute");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit minute value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-59"));
        }
    }

    @Test
    public void testEntryRuleSatisfiedOnCorrectMinute() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, 15, 45);
        TradingRecord tradingRecord = new BaseTradingRecord();

        // Index 15 is minute 15 - entry rule should be satisfied
        assertTrue(strategy.getEntryRule().isSatisfied(15, tradingRecord));

        // Index 20 is minute 20 - entry rule should not be satisfied
        assertFalse(strategy.getEntryRule().isSatisfied(20, tradingRecord));

        // Index 45 is minute 45 - entry rule should not be satisfied (it's exit minute)
        assertFalse(strategy.getEntryRule().isSatisfied(45, tradingRecord));
    }

    @Test
    public void testExitRuleSatisfiedOnCorrectMinute() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, 15, 45);
        TradingRecord tradingRecord = new BaseTradingRecord();

        // Index 15 is minute 15 - exit rule should not be satisfied
        assertFalse(strategy.getExitRule().isSatisfied(15, tradingRecord));

        // Index 45 is minute 45 - exit rule should be satisfied
        assertTrue(strategy.getExitRule().isSatisfied(45, tradingRecord));

        // Index 20 is minute 20 - exit rule should not be satisfied
        assertFalse(strategy.getExitRule().isSatisfied(20, tradingRecord));
    }

    @Test
    public void testBuildAllStrategyPermutations() {
        List<Strategy> strategies = MinuteOfHourStrategy.buildAllStrategyPermutations(series);

        assertNotNull(strategies);
        // Should have 60 * 59 = 3540 strategies (all combinations except where entry ==
        // exit)
        assertEquals(3540, strategies.size());

        // Verify all strategies have unique names
        long uniqueNames = strategies.stream().map(Strategy::getName).distinct().count();
        assertEquals(3540, uniqueNames);

        // Verify all strategies are MinuteOfHourStrategy instances
        for (Strategy strategy : strategies) {
            assertTrue(strategy instanceof MinuteOfHourStrategy);
            assertNotNull(strategy.getName());
            assertTrue(strategy.getName().startsWith("MinuteOfHourStrategy_"));
        }
    }

    @Test
    public void testBuildAllStrategyPermutationsNoDuplicateEntryExit() {
        List<Strategy> strategies = MinuteOfHourStrategy.buildAllStrategyPermutations(series);

        // Verify no strategy has the same entry and exit minute
        for (Strategy strategy : strategies) {
            String name = strategy.getName();
            String[] parts = name.split("_");
            assertNotEquals("Strategy should not have same entry and exit minute: " + name, parts[1], parts[2]);
        }
    }

    @Test
    public void testStrategyNameFormat() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, 20, 40);
        String name = strategy.getName();

        assertTrue(name.startsWith("MinuteOfHourStrategy_"));
        assertTrue(name.contains("20"));
        assertTrue(name.contains("40"));
        assertEquals("MinuteOfHourStrategy_20_40", name);
    }

    @Test
    public void testStrategyWithBoundaryMinutes() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, 0, 59);

        assertNotNull(strategy);
        assertEquals("MinuteOfHourStrategy_0_59", strategy.getName());

        TradingRecord tradingRecord = new BaseTradingRecord();
        // Index 0 is minute 0
        assertTrue(strategy.getEntryRule().isSatisfied(0, tradingRecord));
        // Index 59 is minute 59
        assertTrue(strategy.getExitRule().isSatisfied(59, tradingRecord));
    }

    @Test
    public void testStrategyWithAllMinuteCombinations() {
        for (int entryMinute = 0; entryMinute < 60; entryMinute++) {
            for (int exitMinute = 0; exitMinute < 60; exitMinute++) {
                if (entryMinute != exitMinute) {
                    MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, entryMinute, exitMinute);
                    assertNotNull(strategy);
                    assertNotNull(strategy.getEntryRule());
                    assertNotNull(strategy.getExitRule());
                }
            }
        }
    }

    @Test
    public void testStrategyRulesAreNotNull() {
        MinuteOfHourStrategy strategy = new MinuteOfHourStrategy(series, 20, 40);

        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testStrategyWithNullSeries() {
        try {
            new MinuteOfHourStrategy(null, 15, 45);
            fail("Expected NullPointerException or IllegalArgumentException for null series");
        } catch (Exception e) {
            // Expected - either NullPointerException or IllegalArgumentException
            assertTrue(e instanceof NullPointerException || e instanceof IllegalArgumentException);
        }
    }

    @Test
    public void testParseEntryMinuteErrorMessage() {
        try {
            new MinuteOfHourStrategy(series, "INVALID", "45");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry minute value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-59"));
        }
    }

    @Test
    public void testParseExitMinuteErrorMessage() {
        try {
            new MinuteOfHourStrategy(series, "15", "INVALID");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit minute value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-59"));
        }
    }

    @Test
    public void testInsufficientParamsErrorMessage() {
        try {
            new MinuteOfHourStrategy(series, "15");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("At least 2 parameters required"));
        }
    }

    @Test
    public void testEntryMinuteOutOfRangeErrorMessage() {
        try {
            new MinuteOfHourStrategy(series, "60", "45");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid entry minute value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-59"));
        }
    }

    @Test
    public void testExitMinuteOutOfRangeErrorMessage() {
        try {
            new MinuteOfHourStrategy(series, "15", "60");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Invalid exit minute value"));
            assertTrue(e.getMessage().contains("Valid values are integers in the range 0-59"));
        }
    }
}
