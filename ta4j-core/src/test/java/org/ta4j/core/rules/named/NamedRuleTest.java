/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.rules.named;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Rule;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NamedRuleTest {

    @AfterEach
    void tearDown() {
        NamedRule.unregisterImplementation(TestUnregisterRule.class);
    }

    @Test
    void buildLabelRejectsUnderscoreParameters() {
        assertThatThrownBy(() -> NamedRule.buildLabel(NamedRuleFixture.class, "ABOVE", "10_value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Named rule parameters cannot contain underscores: parameters[1]");
    }

    @Test
    void splitLabelRejectsBlankValues() {
        assertThatThrownBy(() -> NamedRule.splitLabel("")).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Named rule label cannot be blank");
    }

    @Test
    void splitLabelPreservesEmptyEdgeTokens() {
        List<String> leading = NamedRule.splitLabel("_param");
        List<String> trailing = NamedRule.splitLabel("Rule_");

        assertThat(leading).containsExactly("", "param");
        assertThat(trailing).containsExactly("Rule", "");
    }

    @Test
    void varargsConstructorRebuildsNamedRule() {
        BarSeries series = new MockBarSeriesBuilder().withData(1d, 2d, 3d).build();

        NamedRuleFixture rule = new NamedRuleFixture(series, "ABOVE", "2");

        assertThat(rule.getName()).isEqualTo("NamedRuleFixture_ABOVE_2");
        assertThat(rule.getComparison()).isEqualTo(NamedRuleFixture.Comparison.ABOVE);
        assertThat(rule.getThreshold()).hasToString("2");
    }

    @Test
    void lookupAndUnregisterRoundTrip() {
        NamedRule.registerImplementation(TestUnregisterRule.class);

        assertThat(NamedRule.lookup("TestUnregisterRule")).contains(TestUnregisterRule.class);
        assertThat(NamedRule.unregisterImplementation(TestUnregisterRule.class)).isTrue();
        assertThat(NamedRule.lookup("TestUnregisterRule")).isEmpty();
    }

    @Test
    void requireRegisteredRejectsUnknownRules() {
        assertThatThrownBy(() -> NamedRule.requireRegistered("MissingRule"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(
                        "Unknown named rule 'MissingRule'. Ensure it is registered via NamedRule.registerImplementation() or initializeRegistry().");
    }

    @Test
    void ruleJsonRoundTripsThroughConcreteSerialization() {
        BarSeries series = new MockBarSeriesBuilder().withData(1d, 2d, 3d, 4d).build();
        NamedRuleFixture original = new NamedRuleFixture(series, NamedRuleFixture.Comparison.ABOVE,
                series.numFactory().numOf("2"));

        Rule restored = Rule.fromJson(series, original.toJson());

        assertThat(restored).isInstanceOf(NamedRuleFixture.class);
        assertThat(restored.getName()).isEqualTo(original.getName());
        assertThat(restored.isSatisfied(3)).isTrue();
        assertThat(restored.isSatisfied(0)).isFalse();
    }

    private static final class TestUnregisterRule extends NamedRule {

        private final ClosePredicate closePredicate;

        private TestUnregisterRule(ClosePredicate closePredicate) {
            super(NamedRule.buildLabel(TestUnregisterRule.class, closePredicate.name()));
            this.closePredicate = closePredicate;
        }

        public TestUnregisterRule(BarSeries series, String... parameters) {
            this(ClosePredicate.valueOf(parameters[0]));
        }

        @Override
        public boolean isSatisfied(int index, org.ta4j.core.TradingRecord tradingRecord) {
            return closePredicate == ClosePredicate.ALWAYS;
        }

        private enum ClosePredicate {
            ALWAYS
        }
    }
}
