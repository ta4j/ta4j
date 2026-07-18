/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.named;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.serialization.ComponentDescriptor;

public class NamedAssetRegistryTest {

    @Test
    public void defaultRegistryExpandsNestedRuleExpression() {
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();

        ComponentDescriptor descriptor = registry.toDescriptor(NamedAssetKind.RULE, "CrossedUp(SMA(7),EMA(RSI(14),5))");

        assertThat(descriptor.getType()).isEqualTo("CrossedUpIndicatorRule");
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getComponents().get(0).getParameters()).containsEntry("barCount", 7);
        assertThat(descriptor.getComponents().get(1).getType()).isEqualTo("EMAIndicator");
        assertThat(descriptor.getComponents().get(1).getComponents().get(0).getType()).isEqualTo("RSIIndicator");
    }

    @Test
    public void strategySmaPairIsMacroNotMultiOutputIndicator() {
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();

        ComponentDescriptor strategy = registry.toDescriptor(NamedAssetKind.STRATEGY, "SMA(7,21)");
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> registry.toDescriptor(NamedAssetKind.INDICATOR, "SMA(7,21)"));

        assertThat(strategy.getType()).isEqualTo("BaseStrategy");
        assertThat(strategy.getComponents()).hasSize(2);
        ComponentDescriptor entry = strategy.getComponents().get(0);
        ComponentDescriptor exit = strategy.getComponents().get(1);
        assertThat(entry.getType()).isEqualTo("CrossedUpIndicatorRule");
        assertThat(exit.getType()).isEqualTo("CrossedDownIndicatorRule");
        assertThat(entry.getComponents().get(0).getParameters()).containsEntry("barCount", 7);
        assertThat(entry.getComponents().get(1).getParameters()).containsEntry("barCount", 21);
        assertThat(exit.getComponents().get(0).getParameters()).containsEntry("barCount", 7);
        assertThat(exit.getComponents().get(1).getParameters()).containsEntry("barCount", 21);
        assertThat(exception).hasMessageContaining("indicator.args[0]");
    }

    @Test
    public void smaCrossDownKeepsFastThenSlowOperandOrder() {
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();

        ComponentDescriptor descriptor = registry.toDescriptor(NamedAssetKind.RULE, "SmaCrossDown(7,21)");

        assertThat(descriptor.getType()).isEqualTo("CrossedDownIndicatorRule");
        assertThat(descriptor.getComponents()).hasSize(2);
        assertThat(descriptor.getComponents().get(0).getParameters()).containsEntry("barCount", 7);
        assertThat(descriptor.getComponents().get(1).getParameters()).containsEntry("barCount", 21);
        assertThat(registry.toExpression(NamedAssetKind.RULE, descriptor)).contains("SmaCrossDown(7,21)");
    }

    @Test
    public void customIndicatorAliasExpandsThroughImmutableRegistry() {
        NamedAssetRegistry registry = NamedAssetRegistry.builder()
                .registerIndicator("FastCloseSma", List.of("barCount"), args -> {
                    args.requireCount(1);
                    return ComponentDescriptor.builder()
                            .withType("SMAIndicator")
                            .withParameters(Map.of("barCount", args.positiveInt(0)))
                            .addComponent(ComponentDescriptor.typeOnly("ClosePriceIndicator"))
                            .build();
                })
                .build();

        ComponentDescriptor descriptor = registry.toDescriptor(NamedAssetKind.INDICATOR, "FastCloseSma(5)");

        assertThat(descriptor.getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getParameters()).containsEntry("barCount", 5);
    }

    @Test
    public void customAliasCanReadQuotedAndBareStringArguments() {
        NamedAssetRegistry registry = NamedAssetRegistry.builder()
                .registerAnalysisCriterion("WindowedScore", List.of("label", "mode"), args -> {
                    args.requireCount(2);
                    return ComponentDescriptor.builder()
                            .withType("WindowedScoreCriterion")
                            .withParameters(Map.of("label", args.stringValue(0), "mode", args.stringValue(1)))
                            .build();
                })
                .build();

        ComponentDescriptor descriptor = registry.toDescriptor(NamedAssetKind.ANALYSIS_CRITERION,
                "WindowedScore(\"a,b\",LONG)");

        assertThat(descriptor.getParameters()).containsEntry("label", "a,b").containsEntry("mode", "LONG");
    }

    @Test
    public void integerArgumentsUseJsonNumberGrammar() {
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();

        IllegalArgumentException plusPrefixed = assertThrows(IllegalArgumentException.class,
                () -> registry.toDescriptor(NamedAssetKind.INDICATOR, "SMA(+7)"));
        IllegalArgumentException leadingZero = assertThrows(IllegalArgumentException.class,
                () -> registry.toDescriptor(NamedAssetKind.INDICATOR, "SMA(07)"));

        assertThat(plusPrefixed).hasMessageContaining("indicator.args[0]").hasMessageContaining("+7");
        assertThat(leadingZero).hasMessageContaining("indicator.args[0]").hasMessageContaining("07");
    }

    @Test
    public void finiteNumberArgumentsAcceptNumbersOutsideDoubleRange() {
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();
        String authored = "1e309";
        String normalized = new BigDecimal(authored).stripTrailingZeros().toPlainString();

        ComponentDescriptor over = registry.toDescriptor(NamedAssetKind.RULE, "Over(ClosePrice," + authored + ")");
        ComponentDescriptor stopLoss = registry.toDescriptor(NamedAssetKind.RULE, "StopLoss(" + authored + "%)");

        assertThat(over.getComponents().get(1).getType()).isEqualTo("ConstantIndicator");
        assertThat(over.getComponents().get(1).getParameters()).containsEntry("value", normalized);
        assertThat(stopLoss.getParameters()).containsEntry("lossPercentage", normalized);
    }

    @Test
    public void splitTopLevelIgnoresNestedExpressionCommas() {
        NamedAssetRegistry registry = NamedAssetRegistry.defaultRegistry();

        List<String> entries = registry
                .splitTopLevel("NetProfit,ReturnOverMaxDrawdown(MaximumDrawdown),Custom(\"a,b\",SMA(7,21))");

        assertThat(entries).containsExactly("NetProfit", "ReturnOverMaxDrawdown(MaximumDrawdown)",
                "Custom(\"a,b\",SMA(7,21))");
    }

    @Test
    public void duplicateAliasThrowsDuringBuild() {
        NamedAssetRegistry.Builder builder = NamedAssetRegistry.builder()
                .registerAnalysisCriterion("Score", List.of(), args -> ComponentDescriptor.typeOnly("First"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> builder
                .registerAnalysisCriterion("Score", List.of(), args -> ComponentDescriptor.typeOnly("Second")));

        assertThat(exception).hasMessageContaining("already registered").hasMessageContaining("Score");
    }
}
