/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WeightedValueTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    public void constructorRejectsInvalidWeight() {
        assertThrows(NullPointerException.class, () -> new WeightedValue<>(null, NUM_FACTORY.one()));
        assertThrows(NullPointerException.class, () -> new WeightedValue<>("a", null));
        assertThrows(IllegalArgumentException.class, () -> new WeightedValue<>("a", NaN.NaN));
        assertThrows(IllegalArgumentException.class,
                () -> new WeightedValue<>("a", NUM_FACTORY.numOf(Double.POSITIVE_INFINITY)));
    }

    @Test
    public void constructorAllowsNegativeWeightForPenaltyUseCases() {
        WeightedValue<String> weightedValue = new WeightedValue<>("penaltyMetric", NUM_FACTORY.minusOne());
        assertThat(weightedValue.weight()).isEqualByComparingTo(NUM_FACTORY.minusOne());
    }

    @Test
    public void normalizeWeightsReturnsUnitSumInOriginalOrder() {
        List<WeightedValue<String>> normalized = WeightedValue
                .normalizeWeights(List.of(new WeightedValue<>("alpha", NUM_FACTORY.numOf(3.0)),
                        new WeightedValue<>("beta", NUM_FACTORY.numOf(1.0))), NUM_FACTORY);

        assertThat(normalized).hasSize(2);
        assertThat(normalized.get(0).value()).isEqualTo("alpha");
        assertThat(normalized.get(1).value()).isEqualTo("beta");
        assertThat(normalized.get(0).weight()).isEqualByComparingTo(NUM_FACTORY.numOf(0.75));
        assertThat(normalized.get(1).weight()).isEqualByComparingTo(NUM_FACTORY.numOf(0.25));
    }

    @Test
    public void normalizeWeightsRejectsEmptyAndZeroTotals() {
        assertThrows(IllegalArgumentException.class, () -> WeightedValue.normalizeWeights(List.of(), NUM_FACTORY));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedValue.normalizeWeights(List.of(new WeightedValue<>("alpha", NUM_FACTORY.zero()),
                        new WeightedValue<>("beta", NUM_FACTORY.zero())), NUM_FACTORY));
    }

    @Test
    public void weightedSumSkipsMissingValues() {
        List<WeightedValue<String>> weights = List.of(new WeightedValue<>("alpha", NUM_FACTORY.numOf(2.0)),
                new WeightedValue<>("beta", NUM_FACTORY.numOf(1.0)));

        Num weightedSum = WeightedValue.weightedSum(weights, key -> {
            if ("alpha".equals(key)) {
                return NUM_FACTORY.numOf(3.0);
            }
            return NaN.NaN;
        }, NUM_FACTORY);

        assertThat(weightedSum).isEqualByComparingTo(NUM_FACTORY.numOf(6.0));
    }
}
