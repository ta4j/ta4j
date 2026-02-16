/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.ConcurrentBarSeries;
import org.ta4j.core.ConcurrentBarSeriesBuilder;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HighRewardElliottWaveStrategyTest {

    private ConcurrentBarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        series = new ConcurrentBarSeriesBuilder().withName("elliott-test").withNumFactory(numFactory).build();
        Instant start = Instant.parse("2025-01-01T00:00:00Z");
        for (int i = 0; i < 30; i++) {
            BigDecimal close = BigDecimal.valueOf(100.0 + i);
            addBar(series, start.plusSeconds(i * 60L), close);
        }
    }

    @Test
    void testDefaultLabelContainsDirectionAndDegree() {
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series);
        String[] parts = strategy.getName().split("_");
        assertEquals("HighRewardElliottWaveStrategy", parts[0]);
        assertEquals("BULLISH", parts[1]);
        assertEquals("PRIMARY", parts[2]);
        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    void testConstructorWithParamsBuildsExpectedLabel() {
        String[] params = new String[] { "BULLISH", "PRIMARY", "0.7", "3", "1.5", "0.2", "5", "2", "50", "2", "4",
                "0.2" };
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, params);
        String[] parts = strategy.getName().split("_");
        assertEquals("HighRewardElliottWaveStrategy", parts[0]);
        assertEquals("BULLISH", parts[1]);
        assertEquals("PRIMARY", parts[2]);
        assertEquals("0.7", parts[3]);
        assertEquals("3", parts[4]);
        assertEquals("1.5", parts[5]);
        assertEquals("0.2", parts[6]);
        assertEquals("5", parts[7]);
        assertEquals("2", parts[8]);
        assertEquals("50", parts[9]);
        assertEquals("2", parts[10]);
        assertEquals("4", parts[11]);
        assertEquals("0.2", parts[12]);
    }

    @Test
    void testConstructorRejectsInvalidDirection() {
        assertThrows(IllegalArgumentException.class, () -> new HighRewardElliottWaveStrategy(series, "SIDEWAYS",
                "PRIMARY", "0.7", "3", "1.5", "0.2", "5", "2", "50", "2", "4", "0.2"));
    }

    @Test
    void testConstructorRejectsWrongParamCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new HighRewardElliottWaveStrategy(series, "BULLISH", "PRIMARY"));
    }

    @Test
    void testEntryRuleSatisfiedForHighConfidenceImpulse() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenario scenario = buildScenario(numFactory.numOf(120), numFactory.numOf(200));
        ElliottScenarioSet scenarioSet = buildScenarioSet(series, scenario);
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, scenarioSet);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        TradingRecord record = new BaseTradingRecord();
        assertTrue(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    void testEntryRuleRejectedWhenRiskRewardTooLow() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenario scenario = buildScenario(numFactory.numOf(120), numFactory.numOf(140));
        ElliottScenarioSet scenarioSet = buildScenarioSet(series, scenario);
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, scenarioSet);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        TradingRecord record = new BaseTradingRecord();
        assertFalse(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    void testEntryRuleRejectedWhenNoScenario() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenarioSet scenarioSet = buildEmptyScenarioSet(series);
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, scenarioSet);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        TradingRecord record = new BaseTradingRecord();
        assertFalse(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    void testExitRuleTriggersOnInvalidation() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenario scenario = buildScenario(numFactory.numOf(130), numFactory.numOf(200));
        ElliottScenarioSet scenarioSet = buildScenarioSet(series, scenario);
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, scenarioSet);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        BaseTradingRecord record = new BaseTradingRecord();
        int entryIndex = series.getEndIndex() - 1;
        record.enter(entryIndex);

        assertTrue(strategy.getExitRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    void testExitRuleTriggersOnCorrectiveStopViolation() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 4, numFactory.numOf(100), numFactory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 6, numFactory.numOf(150), numFactory.numOf(140), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 12, numFactory.numOf(140), numFactory.numOf(160), ElliottDegree.PRIMARY),
                new ElliottSwing(12, 16, numFactory.numOf(160), numFactory.numOf(150), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 20, numFactory.numOf(150), numFactory.numOf(170), ElliottDegree.PRIMARY));

        ElliottScenario scenario = ElliottScenario.builder()
                .id("test-stop")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(swings)
                .confidence(buildConfidence(numFactory, 0.8))
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(numFactory.numOf(120))
                .primaryTarget(numFactory.numOf(200))
                .fibonacciTargets(List.of(numFactory.numOf(200)))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();

        ElliottScenarioSet scenarioSet = buildScenarioSet(series, scenario);
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, scenarioSet);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        BaseTradingRecord record = new BaseTradingRecord();
        int entryIndex = series.getEndIndex() - 1;
        record.enter(entryIndex);

        assertTrue(strategy.getExitRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    void testExitRuleTriggersWhenNoScenario() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenarioSet scenarioSet = buildEmptyScenarioSet(series);
        Indicator<ElliottScenarioSet> indicator = new FixedScenarioIndicator(series, scenarioSet);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        BaseTradingRecord record = new BaseTradingRecord();
        int entryIndex = series.getEndIndex() - 1;
        record.enter(entryIndex);

        assertTrue(strategy.getExitRule().isSatisfied(series.getEndIndex(), record));
    }

    private ElliottScenario buildScenario(Num invalidation, Num target) {
        List<ElliottSwing> swings = List.of(
                new ElliottSwing(0, 4, numFactory.numOf(100), numFactory.numOf(120), ElliottDegree.PRIMARY),
                new ElliottSwing(4, 6, numFactory.numOf(120), numFactory.numOf(110), ElliottDegree.PRIMARY),
                new ElliottSwing(6, 12, numFactory.numOf(110), numFactory.numOf(145), ElliottDegree.PRIMARY),
                new ElliottSwing(12, 16, numFactory.numOf(145), numFactory.numOf(130), ElliottDegree.PRIMARY),
                new ElliottSwing(16, 20, numFactory.numOf(130), numFactory.numOf(160), ElliottDegree.PRIMARY));

        ElliottConfidence confidence = buildConfidence(numFactory, 0.8);

        return ElliottScenario.builder()
                .id("test")
                .currentPhase(ElliottPhase.WAVE3)
                .swings(swings)
                .confidence(confidence)
                .degree(ElliottDegree.PRIMARY)
                .invalidationPrice(invalidation)
                .primaryTarget(target)
                .fibonacciTargets(List.of(target))
                .type(ScenarioType.IMPULSE)
                .startIndex(0)
                .build();
    }

    private ElliottScenarioSet buildScenarioSet(BarSeries series, ElliottScenario scenario) {
        return ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
    }

    private ElliottScenarioSet buildEmptyScenarioSet(BarSeries series) {
        return ElliottScenarioSet.empty(series.getEndIndex());
    }

    private ElliottConfidence buildConfidence(NumFactory factory, double overall) {
        Num score = factory.numOf(overall);
        return new ElliottConfidence(score, score, score, score, score, score, "test");
    }

    private static void addBar(ConcurrentBarSeries series, Instant start, BigDecimal close) {
        Instant end = start.plusSeconds(60L);
        BigDecimal high = close.add(new BigDecimal("0.5"));
        BigDecimal low = close.subtract(new BigDecimal("0.5"));
        series.addBar(buildBar(series, start, end, close, high, low, close));
    }

    private static Bar buildBar(ConcurrentBarSeries series, Instant start, Instant end, BigDecimal open,
            BigDecimal high, BigDecimal low, BigDecimal close) {
        return series.barBuilder()
                .timePeriod(Duration.between(start, end))
                .beginTime(start)
                .endTime(end)
                .openPrice(open)
                .highPrice(high)
                .lowPrice(low)
                .closePrice(close)
                .volume(new BigDecimal("1"))
                .build();
    }

    private static final class FixedScenarioIndicator implements Indicator<ElliottScenarioSet> {

        private final BarSeries series;
        private final ElliottScenarioSet scenarioSet;

        private FixedScenarioIndicator(BarSeries series, ElliottScenarioSet scenarioSet) {
            this.series = series;
            this.scenarioSet = scenarioSet;
        }

        @Override
        public ElliottScenarioSet getValue(int index) {
            return scenarioSet;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        public BarSeries getBarSeries() {
            return series;
        }
    }
}
