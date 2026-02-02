/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.strategies;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.elliott.ElliottAnalysisResult;
import org.ta4j.core.indicators.elliott.ElliottChannel;
import org.ta4j.core.indicators.elliott.ElliottConfidence;
import org.ta4j.core.indicators.elliott.ElliottDegree;
import org.ta4j.core.indicators.elliott.ElliottPhase;
import org.ta4j.core.indicators.elliott.ElliottScenario;
import org.ta4j.core.indicators.elliott.ElliottScenarioSet;
import org.ta4j.core.indicators.elliott.ElliottSwing;
import org.ta4j.core.indicators.elliott.ElliottTrendBias;
import org.ta4j.core.indicators.elliott.ScenarioType;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HighRewardElliottWaveStrategyTest {

    private BarSeries series;
    private NumFactory numFactory;

    @Before
    public void setUp() {
        numFactory = DecimalNumFactory.getInstance();
        List<Double> data = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            data.add(100.0 + i);
        }
        series = new MockBarSeriesBuilder().withNumFactory(numFactory).withData(data).build();
    }

    @Test
    public void testDefaultLabelContainsDirectionAndDegree() {
        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series);
        String[] parts = strategy.getName().split("_");
        assertEquals("HighRewardElliottWaveStrategy", parts[0]);
        assertEquals("BULLISH", parts[1]);
        assertEquals("PRIMARY", parts[2]);
        assertNotNull(strategy.getEntryRule());
        assertNotNull(strategy.getExitRule());
    }

    @Test
    public void testConstructorWithParamsBuildsExpectedLabel() {
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
    public void testConstructorRejectsInvalidDirection() {
        assertThrows(IllegalArgumentException.class, () -> new HighRewardElliottWaveStrategy(series, "SIDEWAYS",
                "PRIMARY", "0.7", "3", "1.5", "0.2", "5", "2", "50", "2", "4", "0.2"));
    }

    @Test
    public void testConstructorRejectsWrongParamCount() {
        assertThrows(IllegalArgumentException.class,
                () -> new HighRewardElliottWaveStrategy(series, "BULLISH", "PRIMARY"));
    }

    @Test
    public void testEntryRuleSatisfiedForHighConfidenceImpulse() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenario scenario = buildScenario(numFactory.numOf(120), numFactory.numOf(200));
        ElliottAnalysisResult result = buildResult(series, scenario);
        Indicator<ElliottAnalysisResult> indicator = new FixedAnalysisIndicator(series, result);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        TradingRecord record = new BaseTradingRecord();
        assertTrue(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    public void testEntryRuleRejectedWhenRiskRewardTooLow() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenario scenario = buildScenario(numFactory.numOf(120), numFactory.numOf(140));
        ElliottAnalysisResult result = buildResult(series, scenario);
        Indicator<ElliottAnalysisResult> indicator = new FixedAnalysisIndicator(series, result);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        TradingRecord record = new BaseTradingRecord();
        assertFalse(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    public void testEntryRuleRejectedWhenNoScenario() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottAnalysisResult result = buildEmptyResult(series);
        Indicator<ElliottAnalysisResult> indicator = new FixedAnalysisIndicator(series, result);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        TradingRecord record = new BaseTradingRecord();
        assertFalse(strategy.getEntryRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    public void testExitRuleTriggersOnInvalidation() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottScenario scenario = buildScenario(numFactory.numOf(130), numFactory.numOf(200));
        ElliottAnalysisResult result = buildResult(series, scenario);
        Indicator<ElliottAnalysisResult> indicator = new FixedAnalysisIndicator(series, result);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        BaseTradingRecord record = new BaseTradingRecord();
        int entryIndex = series.getEndIndex() - 1;
        record.enter(entryIndex);

        assertTrue(strategy.getExitRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    public void testExitRuleTriggersOnCorrectiveStopViolation() {
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

        ElliottAnalysisResult result = buildResult(series, scenario);
        Indicator<ElliottAnalysisResult> indicator = new FixedAnalysisIndicator(series, result);

        HighRewardElliottWaveStrategy strategy = new HighRewardElliottWaveStrategy(series, config, indicator);
        BaseTradingRecord record = new BaseTradingRecord();
        int entryIndex = series.getEndIndex() - 1;
        record.enter(entryIndex);

        assertTrue(strategy.getExitRule().isSatisfied(series.getEndIndex(), record));
    }

    @Test
    public void testExitRuleTriggersWhenNoScenario() {
        HighRewardElliottWaveStrategy.Config config = new HighRewardElliottWaveStrategy.Config(
                HighRewardElliottWaveStrategy.SignalDirection.BULLISH, ElliottDegree.PRIMARY, 0.7, 3.0, 1.5, 0.2, 5, 2,
                50.0, 2, 4, 0.2);

        ElliottAnalysisResult result = buildEmptyResult(series);
        Indicator<ElliottAnalysisResult> indicator = new FixedAnalysisIndicator(series, result);

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

    private ElliottAnalysisResult buildResult(BarSeries series, ElliottScenario scenario) {
        ElliottScenarioSet scenarioSet = ElliottScenarioSet.of(List.of(scenario), series.getEndIndex());
        ElliottTrendBias bias = ElliottTrendBias.fromScenarios(List.of(scenario));
        ElliottChannel channel = new ElliottChannel(numFactory.zero(), numFactory.zero(), numFactory.zero());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), scenario.swings(),
                scenario.swings(), scenarioSet, Map.of(), channel, bias);
    }

    private ElliottAnalysisResult buildEmptyResult(BarSeries series) {
        ElliottScenarioSet scenarioSet = ElliottScenarioSet.empty(series.getEndIndex());
        ElliottTrendBias bias = ElliottTrendBias.fromScenarios(List.of());
        ElliottChannel channel = new ElliottChannel(numFactory.zero(), numFactory.zero(), numFactory.zero());
        return new ElliottAnalysisResult(ElliottDegree.PRIMARY, series.getEndIndex(), List.of(), List.of(), scenarioSet,
                Map.of(), channel, bias);
    }

    private ElliottConfidence buildConfidence(NumFactory factory, double overall) {
        Num score = factory.numOf(overall);
        return new ElliottConfidence(score, score, score, score, score, score, "test");
    }

    private static final class FixedAnalysisIndicator implements Indicator<ElliottAnalysisResult> {

        private final BarSeries series;
        private final ElliottAnalysisResult result;

        private FixedAnalysisIndicator(BarSeries series, ElliottAnalysisResult result) {
            this.series = series;
            this.result = result;
        }

        @Override
        public ElliottAnalysisResult getValue(int index) {
            return result;
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
