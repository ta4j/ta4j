/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.ConstantIndicator;
import org.ta4j.core.indicators.helpers.CrossIndicator;
import org.ta4j.core.indicators.helpers.DateTimeIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.AndRule;
import org.ta4j.core.rules.BooleanRule;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.DayOfWeekRule;
import org.ta4j.core.rules.FixedRule;
import org.ta4j.core.rules.NotRule;
import org.ta4j.core.rules.OrRule;
import org.ta4j.core.rules.OverIndicatorRule;
import org.ta4j.core.rules.TrailingFixedAmountStopGainRule;
import org.ta4j.core.rules.TrailingFixedAmountStopLossRule;
import org.ta4j.core.rules.UnderIndicatorRule;
import org.ta4j.core.rules.VoteRule;
import org.ta4j.core.rules.XorRule;

/**
 * Comprehensive tests for rule serialization and deserialization.
 *
 * <p>
 * This test class consolidates tests for:
 * <ul>
 * <li>Nested component extraction in rule serialization</li>
 * <li>Varargs parameter serialization</li>
 * <li>Custom name preservation</li>
 * <li>AndRule-specific serialization scenarios</li>
 * </ul>
 */
public class RuleSerializationTest {

    // ==================== Nested Components Tests ====================

    /**
     * Tests for nested component extraction in rule serialization.
     *
     * <p>
     * These tests verify that composite indicators (like {@link CrossIndicator})
     * stored as fields in rules are correctly "unwrapped" to extract their nested
     * components via getter methods, so they can be matched against constructor
     * parameters during deserialization.
     */
    @Test
    public void extractNestedComponentsFromCrossIndicatorInCrossedUpRule() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule rule = new CrossedUpIndicatorRule(shortSma, longSma);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify the rule type is correct
        assertThat(descriptor.getType()).isEqualTo("CrossedUpIndicatorRule");

        // Verify that nested components from CrossIndicator are extracted
        // The rule stores a CrossIndicator, but the constructor takes two separate
        // indicators
        // So we should see both indicators in the components list
        assertThat(descriptor.getComponents()).hasSize(2);

        // Verify both indicators are present (order may vary, so check types)
        ComponentDescriptor first = descriptor.getComponents().get(0);
        ComponentDescriptor second = descriptor.getComponents().get(1);

        assertThat(first.getType()).isEqualTo("SMAIndicator");
        assertThat(second.getType()).isEqualTo("SMAIndicator");

        // Verify the barCount parameters are preserved
        boolean foundShort = false;
        boolean foundLong = false;
        for (ComponentDescriptor component : descriptor.getComponents()) {
            if (component.getType().equals("SMAIndicator")) {
                Object barCount = component.getParameters().get("barCount");
                if (barCount != null) {
                    if (barCount.equals(2)) {
                        foundShort = true;
                    } else if (barCount.equals(3)) {
                        foundLong = true;
                    }
                }
            }
        }
        assertThat(foundShort).as("Should find SMAIndicator with barCount=2").isTrue();
        assertThat(foundLong).as("Should find SMAIndicator with barCount=3").isTrue();
    }

    @Test
    public void extractNestedComponentsFromCrossIndicatorInCrossedDownRule() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator first = new SMAIndicator(close, 2);
        SMAIndicator second = new SMAIndicator(close, 3);

        Rule rule = new CrossedDownIndicatorRule(first, second);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify the rule type is correct
        assertThat(descriptor.getType()).isEqualTo("CrossedDownIndicatorRule");

        // Verify that nested components from CrossIndicator are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getComponents().get(1).getType()).isEqualTo("SMAIndicator");
    }

    @Test
    public void roundTripCrossedUpIndicatorRuleWithNestedComponents() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule original = new CrossedUpIndicatorRule(shortSma, longSma);

        // Serialize and deserialize
        ComponentDescriptor descriptor = RuleSerialization.describe(original);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        // Verify the restored rule is of the correct type
        assertThat(restored).isInstanceOf(CrossedUpIndicatorRule.class);

        // Verify the rule behaves the same way
        CrossedUpIndicatorRule originalRule = (CrossedUpIndicatorRule) original;
        CrossedUpIndicatorRule restoredRule = (CrossedUpIndicatorRule) restored;

        // Both should have the same nested indicators accessible via getters
        Indicator<Num> originalLow = originalRule.getLow();
        Indicator<Num> originalUp = originalRule.getUp();
        Indicator<Num> restoredLow = restoredRule.getLow();
        Indicator<Num> restoredUp = restoredRule.getUp();

        assertThat(originalLow).isNotNull();
        assertThat(originalUp).isNotNull();
        assertThat(restoredLow).isNotNull();
        assertThat(restoredUp).isNotNull();

        // Verify the indicators have the same values at various indices
        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(restored.isSatisfied(i)).as("Rule should behave the same at index %d", i)
                    .isEqualTo(original.isSatisfied(i));
        }
    }

    @Test
    public void roundTripCrossedDownIndicatorRuleWithNestedComponents() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator first = new SMAIndicator(close, 2);
        SMAIndicator second = new SMAIndicator(close, 3);

        Rule original = new CrossedDownIndicatorRule(first, second);

        // Serialize and deserialize
        ComponentDescriptor descriptor = RuleSerialization.describe(original);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        // Verify the restored rule is of the correct type
        assertThat(restored).isInstanceOf(CrossedDownIndicatorRule.class);

        // Verify the rule behaves the same way
        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(restored.isSatisfied(i)).as("Rule should behave the same at index %d", i)
                    .isEqualTo(original.isSatisfied(i));
        }
    }

    @Test
    public void extractNestedComponentsWithConstantIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        ConstantIndicator<Num> constant = new ConstantIndicator<>(series, series.numFactory().numOf(10));

        Rule rule = new CrossedUpIndicatorRule(close, constant);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify nested components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
        assertThat(descriptor.getComponents().get(1).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
    }

    @Test
    public void extractNestedComponentsWithNumberThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);

        // Use constructor that takes Number threshold (creates ConstantIndicator
        // internally)
        Rule rule = new CrossedUpIndicatorRule(close, 10);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify nested components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        // One should be ClosePriceIndicator, the other should be ConstantIndicator
        boolean foundClose = false;
        boolean foundConstant = false;
        for (ComponentDescriptor component : descriptor.getComponents()) {
            if (component.getType().equals("ClosePriceIndicator")) {
                foundClose = true;
            } else if (component.getType().equals("ConstantIndicator")) {
                foundConstant = true;
            }
        }
        assertThat(foundClose).isTrue();
        assertThat(foundConstant).isTrue();
    }

    @Test
    public void extractNestedComponentsWithNumThreshold() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        Num threshold = series.numFactory().numOf(10);

        // Use constructor that takes Num threshold (creates ConstantIndicator
        // internally)
        Rule rule = new CrossedUpIndicatorRule(close, threshold);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify nested components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
        assertThat(descriptor.getComponents().get(1).getType()).isIn("ClosePriceIndicator", "ConstantIndicator");
    }

    @Test
    public void nestedComponentsExtractionPreservesComponentOrder() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator indicator1 = new SMAIndicator(close, 2);
        SMAIndicator indicator2 = new SMAIndicator(close, 3);

        Rule rule = new CrossedUpIndicatorRule(indicator1, indicator2);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        // Verify components are extracted
        assertThat(descriptor.getComponents()).hasSize(2);

        // Round-trip should preserve functionality
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);
        ComponentDescriptor restoredDescriptor = RuleSerialization.describe(restored);

        assertThat(restoredDescriptor.getComponents()).hasSize(2);
    }

    @Test
    public void nestedComponentsExtractionWorksInStrategyContext() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator shortSma = new SMAIndicator(close, 2);
        SMAIndicator longSma = new SMAIndicator(close, 3);

        Rule entry = new CrossedUpIndicatorRule(shortSma, longSma);
        Rule exit = new CrossedDownIndicatorRule(shortSma, longSma);

        org.ta4j.core.Strategy strategy = new org.ta4j.core.BaseStrategy("Test", entry, exit, 1);

        // Serialize strategy
        ComponentDescriptor strategyDescriptor = strategy.toDescriptor();

        // Verify entry rule has nested components extracted
        ComponentDescriptor entryDescriptor = strategyDescriptor.getComponents().get(0);
        assertThat(entryDescriptor.getType()).isEqualTo("CrossedUpIndicatorRule");
        assertThat(entryDescriptor.getComponents()).hasSize(2);

        // Verify exit rule has nested components extracted
        ComponentDescriptor exitDescriptor = strategyDescriptor.getComponents().get(1);
        assertThat(exitDescriptor.getType()).isEqualTo("CrossedDownIndicatorRule");
        assertThat(exitDescriptor.getComponents()).hasSize(2);

        // Round-trip the strategy
        org.ta4j.core.Strategy restoredStrategy = org.ta4j.core.serialization.StrategySerialization
                .fromDescriptor(series, strategyDescriptor);

        // Verify the restored strategy works correctly
        assertThat(restoredStrategy.getName()).isEqualTo("Test");
        assertThat(restoredStrategy.getUnstableBars()).isEqualTo(1);
    }

    @Test
    public void serializeTrailingFixedAmountStopGainRuleKeepsNumericParametersAlignedByName() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(100, 110, 90, 95, 105)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        TrailingFixedAmountStopGainRule rule = new TrailingFixedAmountStopGainRule(close, series.numFactory().numOf(7),
                2);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        assertThat(descriptor.getParameters()).containsEntry("gainAmount", "7").containsEntry("barCount", 2);

        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);
        ComponentDescriptor restoredDescriptor = RuleSerialization.describe(restored);
        assertThat(restoredDescriptor.getParameters()).isEqualTo(descriptor.getParameters());
    }

    @Test
    public void serializeTrailingFixedAmountStopLossRuleKeepsNumericParametersAlignedByName() {
        BarSeries series = new MockBarSeriesBuilder().withNumFactory(DecimalNumFactory.getInstance())
                .withData(100, 110, 90, 95, 105)
                .build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        TrailingFixedAmountStopLossRule rule = new TrailingFixedAmountStopLossRule(close, series.numFactory().numOf(7),
                2);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        assertThat(descriptor.getParameters()).containsEntry("lossAmount", "7").containsEntry("barCount", 2);

        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);
        ComponentDescriptor restoredDescriptor = RuleSerialization.describe(restored);
        assertThat(restoredDescriptor.getParameters()).isEqualTo(descriptor.getParameters());
    }

    @Test
    public void nestedComponentsExtractionHandlesMultipleCompositeIndicators() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator sma1 = new SMAIndicator(close, 2);
        SMAIndicator sma2 = new SMAIndicator(close, 3);
        SMAIndicator sma3 = new SMAIndicator(close, 4);

        // Create rules with different composite indicators
        Rule rule1 = new CrossedUpIndicatorRule(sma1, sma2);
        Rule rule2 = new CrossedDownIndicatorRule(sma2, sma3);

        // Both should serialize correctly with nested components extracted
        ComponentDescriptor desc1 = RuleSerialization.describe(rule1);
        ComponentDescriptor desc2 = RuleSerialization.describe(rule2);

        assertThat(desc1.getComponents()).hasSize(2);
        assertThat(desc2.getComponents()).hasSize(2);

        // Both should round-trip correctly
        Rule restored1 = RuleSerialization.fromDescriptor(series, desc1);
        Rule restored2 = RuleSerialization.fromDescriptor(series, desc2);

        assertThat(restored1).isInstanceOf(CrossedUpIndicatorRule.class);
        assertThat(restored2).isInstanceOf(CrossedDownIndicatorRule.class);
    }

    // ==================== Varargs Tests ====================

    @Test
    public void serializeAndRebuildNumericVarargs() {
        Rule rule = new FixedRule(1, 3, 5);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        Object arrayValues = null;
        for (Map.Entry<String, Object> entry : descriptor.getParameters().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof List<?> list)) {
                continue;
            }
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                continue;
            }
            arrayValues = list;
            break;
        }

        assertThat(arrayValues).as("Expected to find indexes list in descriptor parameters").isNotNull();
        assertThat(arrayValues).isInstanceOf(List.class);
        @SuppressWarnings("unchecked")
        List<Integer> indexes = (List<Integer>) arrayValues;
        assertThat(indexes).containsExactly(1, 3, 5);
        assertThat(descriptor.getParameters()).containsKey("indexes");

        Rule reconstructed = RuleSerialization.fromDescriptor(new MockBarSeriesBuilder().build(), descriptor);
        assertThat(reconstructed).isInstanceOf(FixedRule.class);
        assertThat(reconstructed.isSatisfied(1)).isTrue();
        assertThat(reconstructed.isSatisfied(2)).isFalse();
        assertThat(reconstructed.isSatisfied(5)).isTrue();
    }

    @Test
    public void serializeAndRebuildEnumVarargs() {
        var series = new MockBarSeriesBuilder().build();
        series.barBuilder().endTime(Instant.parse("2024-01-01T12:00:00Z")).add(); // Monday
        series.barBuilder().endTime(Instant.parse("2024-01-02T12:00:00Z")).add(); // Tuesday
        series.barBuilder().endTime(Instant.parse("2024-01-03T12:00:00Z")).add(); // Wednesday
        series.barBuilder().endTime(Instant.parse("2024-01-04T12:00:00Z")).add(); // Thursday
        series.barBuilder().endTime(Instant.parse("2024-01-05T12:00:00Z")).add(); // Friday

        DateTimeIndicator dateTime = new DateTimeIndicator(series, Bar::getEndTime);
        DayOfWeekRule rule = new DayOfWeekRule(dateTime, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);

        assertThat(descriptor.getParameters()).containsKey("daysOfWeek");

        List<String> days = null;
        for (Map.Entry<String, Object> entry : descriptor.getParameters().entrySet()) {
            Object value = entry.getValue();
            if (!(value instanceof List<?> list)) {
                continue;
            }
            if (!list.isEmpty() && list.get(0) instanceof Map<?, ?>) {
                continue;
            }
            boolean allStrings = true;
            for (Object element : list) {
                if (element != null && !(element instanceof String)) {
                    allStrings = false;
                    break;
                }
            }
            if (allStrings) {
                @SuppressWarnings("unchecked")
                List<String> cast = (List<String>) list;
                days = cast;
                break;
            }
        }

        assertThat(days).as("Expected to find daysOfWeek list in descriptor parameters").isNotNull();
        assertThat(days).containsExactlyInAnyOrder("WEDNESDAY", "FRIDAY");

        Rule reconstructed = RuleSerialization.fromDescriptor(series, descriptor);
        assertThat(reconstructed).isInstanceOf(DayOfWeekRule.class);
        Set<DayOfWeek> expected = Set.of(DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);
        Set<DayOfWeek> actual;
        try {
            var field = DayOfWeekRule.class.getDeclaredField("daysOfWeekSet");
            field.setAccessible(true);
            @SuppressWarnings("unchecked")
            Set<DayOfWeek> value = (Set<DayOfWeek>) field.get(reconstructed);
            actual = value;
        } catch (IllegalAccessException | NoSuchFieldException ex) {
            throw new AssertionError("Unable to inspect reconstructed rule", ex);
        }
        assertThat(actual).containsExactlyInAnyOrderElementsOf(expected);
    }

    // ==================== Custom Name Tests ====================

    @Test
    public void preserveCustomNameForSimpleRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule = new FixedRule(1);
        rule.setName("My Custom Rule");

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo("My Custom Rule");
    }

    @Test
    public void preserveCustomNameForCompositeRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule andRule = new AndRule(entryRule, exitRule);
        // AndRule automatically sets its name based on child names
        String originalName = andRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(andRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
        // Verify child rules also preserved their names
        AndRule restoredAnd = (AndRule) restored;
        assertThat(restoredAnd.getRule1().getName()).isEqualTo("Entry");
        assertThat(restoredAnd.getRule2().getName()).isEqualTo("Exit");
    }

    @Test
    public void preserveCustomNameForNestedCompositeRules() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule entryRule = new FixedRule(1);
        entryRule.setName("Entry");
        Rule exitRule = new FixedRule(2);
        exitRule.setName("Exit");

        Rule innerAnd = new AndRule(entryRule, exitRule);
        Rule notExit = new NotRule(exitRule);
        Rule outerOr = new OrRule(innerAnd, notExit);

        String originalName = outerOr.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(outerOr);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    @Test
    public void preserveCustomNameForOrRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule2");

        Rule orRule = new OrRule(rule1, rule2);
        String originalName = orRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(orRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    @Test
    public void preserveCustomNameForXorRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule1 = new FixedRule(1);
        rule1.setName("Rule1");
        Rule rule2 = new FixedRule(2);
        rule2.setName("Rule2");

        Rule xorRule = new XorRule(rule1, rule2);
        String originalName = xorRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(xorRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    @Test
    public void preserveCustomNameForNotRule() {
        BarSeries series = new MockBarSeriesBuilder().build();
        Rule rule = new FixedRule(1);
        rule.setName("MyRule");

        Rule notRule = new NotRule(rule);
        String originalName = notRule.getName();

        ComponentDescriptor descriptor = RuleSerialization.describe(notRule);
        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);

        assertThat(restored.getName()).isEqualTo(originalName);
    }

    // ==================== AndRule-Specific Tests ====================

    private static Fixture newFixture() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 12, 11, 13, 15, 14).build();
        ClosePriceIndicator close = new ClosePriceIndicator(series);
        SMAIndicator slowSma = new SMAIndicator(close, 3);
        SMAIndicator fastSma = new SMAIndicator(close, 2);

        Rule entry = new AndRule(new OverIndicatorRule(fastSma, slowSma), new UnderIndicatorRule(fastSma, slowSma));
        Rule exit = new UnderIndicatorRule(fastSma, slowSma);

        Strategy strategy = new BaseStrategy("Test", entry, exit, 2);

        return new Fixture(series, entry, strategy);
    }

    private static ComponentDescriptor withChildLabels(ComponentDescriptor descriptor) {
        ComponentDescriptor.Builder parent = ComponentDescriptor.builder()
                .withType(descriptor.getType())
                .withParameters(descriptor.getParameters());
        int index = 1;
        for (ComponentDescriptor component : descriptor.getComponents()) {
            ComponentDescriptor.Builder child = ComponentDescriptor.builder()
                    .withType(component.getType())
                    .withLabel("rule" + index++)
                    .withParameters(component.getParameters());
            for (ComponentDescriptor nested : component.getComponents()) {
                child.addComponent(nested);
            }
            parent.addComponent(child.build());
        }
        return parent.build();
    }

    @Test
    public void roundTripAndRuleWithIndicatorRules() {
        Fixture fixture = newFixture();

        // Serialize
        ComponentDescriptor descriptor = RuleSerialization.describe(fixture.andRule());
        ComponentDescriptor parsed = ComponentSerialization.parse(ComponentSerialization.toJson(descriptor));

        // Deserialize
        Rule restored = RuleSerialization.fromDescriptor(fixture.series(), parsed);

        assertThat(restored).isInstanceOf(AndRule.class);
        assertThat(restored.isSatisfied(3)).isEqualTo(fixture.andRule().isSatisfied(3));
    }

    @Test
    public void roundTripAndRuleFromJson() {
        Fixture fixture = newFixture();

        // Serialize to JSON
        ComponentDescriptor descriptor = RuleSerialization.describe(fixture.andRule());
        String json = ComponentSerialization.toJson(descriptor);

        // Deserialize from JSON
        ComponentDescriptor parsed = ComponentSerialization.parse(json);
        Rule restored = RuleSerialization.fromDescriptor(fixture.series(), parsed);

        assertThat(restored).isInstanceOf(AndRule.class);
        assertThat(restored.isSatisfied(3)).isEqualTo(fixture.andRule().isSatisfied(3));
    }

    @Test
    public void deserializeAndRuleWithLabeledComponents() {
        Fixture fixture = newFixture();

        ComponentDescriptor descriptor = RuleSerialization.describe(fixture.andRule());
        ComponentDescriptor labeled = withChildLabels(descriptor);

        Rule restored = RuleSerialization.fromDescriptor(fixture.series(), labeled);

        assertThat(restored).isInstanceOf(AndRule.class);
        assertThat(restored.isSatisfied(3)).isEqualTo(fixture.andRule().isSatisfied(3));
    }

    @Test
    public void roundTripStrategyWithAndRule() {
        Fixture fixture = newFixture();

        String json = fixture.strategy().toJson();
        Strategy restored = Strategy.fromJson(fixture.series(), json);

        assertThat(restored.getName()).isEqualTo("Test");
        assertThat(restored.getUnstableBars()).isEqualTo(2);
        assertThat(restored.getEntryRule()).isInstanceOf(AndRule.class);

        TradingRecord originalRecord = new BaseTradingRecord();
        TradingRecord restoredRecord = new BaseTradingRecord();
        assertThat(restored.shouldEnter(3, restoredRecord))
                .isEqualTo(fixture.strategy().shouldEnter(3, originalRecord));
    }

    // ==================== VoteRule Rule-Array Tests ====================

    @Test
    @SuppressWarnings("unchecked")
    public void describeVoteRuleIncludesRuleArrayMetadata() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();
        VoteRule rule = new VoteRule(2, BooleanRule.TRUE, BooleanRule.FALSE, BooleanRule.TRUE);

        ComponentDescriptor descriptor = RuleSerialization.describe(rule);
        Object metadata = descriptor.getParameters().get("__ruleArray_rules");
        assertThat(metadata).as("VoteRule metadata should list component labels").isInstanceOf(List.class);
        List<String> labels = (List<String>) metadata;
        assertThat(labels).containsExactly("rulesIdx0", "rulesIdx1", "rulesIdx2");
        assertThat(descriptor.getComponents()).hasSize(3).allSatisfy(component -> {
            assertThat(component.getLabel()).isNotNull();
            assertThat(component.getType()).isEqualTo("BooleanRule");
        });

        Rule restored = RuleSerialization.fromDescriptor(series, descriptor);
        assertThat(restored).isInstanceOf(VoteRule.class);
        for (int i = 0; i < series.getBarCount(); i++) {
            assertThat(restored.isSatisfied(i)).isEqualTo(rule.isSatisfied(i));
        }
    }

    @Test
    public void reconstructVoteRuleFromDescriptorMetadata() {
        BarSeries series = new MockBarSeriesBuilder().withData(1).build();

        ComponentDescriptor ruleA = ComponentDescriptor.builder()
                .withType("BooleanRule")
                .withLabel("rulesIdx0")
                .withParameters(Map.of("satisfied", true))
                .build();
        ComponentDescriptor ruleB = ComponentDescriptor.builder()
                .withType("BooleanRule")
                .withLabel("rulesIdx1")
                .withParameters(Map.of("satisfied", false))
                .build();
        ComponentDescriptor ruleC = ComponentDescriptor.builder()
                .withType("BooleanRule")
                .withLabel("rulesIdx2")
                .withParameters(Map.of("satisfied", true))
                .build();

        ComponentDescriptor descriptor = ComponentDescriptor.builder()
                .withType("VoteRule")
                .withParameters(
                        Map.of("requiredVotes", 2, "__ruleArray_rules", List.of("rulesIdx0", "rulesIdx1", "rulesIdx2")))
                .addComponent(ruleA)
                .addComponent(ruleB)
                .addComponent(ruleC)
                .build();

        Rule reconstructed = RuleSerialization.fromDescriptor(series, descriptor);
        assertThat(reconstructed).isInstanceOf(VoteRule.class);
        assertThat(reconstructed.isSatisfied(0)).as("two true votes should satisfy rule").isTrue();

        ComponentDescriptor roundTripped = RuleSerialization.describe(reconstructed);
        assertThat(roundTripped.getParameters().get("__ruleArray_rules"))
                .isEqualTo(descriptor.getParameters().get("__ruleArray_rules"));
    }

    @Test
    public void customNamesAreSerializedOutOfBandAndRestored() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4).build();
        FixedRule left = new FixedRule(1);
        left.setName("left-label");
        FixedRule right = new FixedRule(2);
        Rule composite = new AndRule(left, right);
        composite.setName("my-custom-rule");

        ComponentDescriptor descriptor = RuleSerialization.describe(composite);
        assertThat(descriptor.getLabel()).isEqualTo("my-custom-rule");
        assertThat(descriptor.getParameters()).containsEntry("__customName", "my-custom-rule");

        String json = composite.toJson();
        Rule restored = Rule.fromJson(series, json);
        assertThat(restored.getName()).isEqualTo("my-custom-rule");
        assertThat(((AndRule) restored).getRule1().getName()).isEqualTo("left-label");
    }

    private record Fixture(BarSeries series, Rule andRule, Strategy strategy) {
    }
}
