/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class WeightedValueTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();
    private static final NumFactory DECIMAL_FACTORY = DecimalNumFactory.getInstance();
    private static final NumFactory PRECISE_DECIMAL_FACTORY = DecimalNumFactory.getInstance(32);

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

    @Test
    public void weightedSumPreservesTinyFiniteDecimalWeightThroughDoubleTarget() {
        WeightedValue<String> tiny = new WeightedValue<>("tiny", DECIMAL_FACTORY.numOf("1E-20"));

        Num weightedSum = WeightedValue.weightedSum(List.of(tiny), key -> NUM_FACTORY.one(), NUM_FACTORY);

        assertThat(weightedSum.isZero()).isFalse();
        assertThat(weightedSum.doubleValue()).isEqualTo(1E-20);
    }

    @Test
    public void weightedSumRejectsDecimalWeightUnrepresentableInDoubleTarget() {
        WeightedValue<String> underflow = new WeightedValue<>("tiny", DECIMAL_FACTORY.numOf("1E-400"));
        assertThrows(IllegalArgumentException.class,
                () -> WeightedValue.weightedSum(List.of(underflow), key -> NUM_FACTORY.one(), NUM_FACTORY));

        // A resolved value (not a constructor-validated weight) that overflows the
        // double range must fail loudly instead of producing an infinite sum.
        WeightedValue<String> weight = new WeightedValue<>("huge", NUM_FACTORY.one());
        assertThrows(IllegalArgumentException.class,
                () -> WeightedValue.weightedSum(List.of(weight), key -> DECIMAL_FACTORY.numOf("1E400"), NUM_FACTORY));
    }

    @Test
    public void decimalPrecisionSurvivesDecimalTargetWeightedSum() {
        BigDecimal precise = new BigDecimal("0.12345678901234567890123456789");
        WeightedValue<String> weightedValue = new WeightedValue<>("precise", PRECISE_DECIMAL_FACTORY.numOf(precise));

        Num weightedSum = WeightedValue.weightedSum(List.of(weightedValue), key -> PRECISE_DECIMAL_FACTORY.one(),
                PRECISE_DECIMAL_FACTORY);

        assertThat(weightedSum.bigDecimalValue()).isEqualByComparingTo(precise);
    }

    @Test
    public void normalizeWeightsPreservesDecimalMantissaDigits() {
        BigDecimal precise = new BigDecimal("0.12345678901234567890123456789");
        BigDecimal complement = BigDecimal.ONE.subtract(precise);
        List<WeightedValue<String>> normalized = WeightedValue.normalizeWeights(
                List.of(new WeightedValue<>("precise", PRECISE_DECIMAL_FACTORY.numOf(precise)),
                        new WeightedValue<>("complement", PRECISE_DECIMAL_FACTORY.numOf(complement))),
                PRECISE_DECIMAL_FACTORY);

        assertThat(normalized.get(0).weight().bigDecimalValue()).isEqualByComparingTo(precise);
        assertThat(normalized.get(1).weight().bigDecimalValue()).isEqualByComparingTo(complement);
    }

    @Test
    public void doubleValueThroughDecimalTargetConvertsViaBigDecimal() {
        WeightedValue<String> weightedValue = new WeightedValue<>("v", PRECISE_DECIMAL_FACTORY.one());

        Num weightedSum = WeightedValue.weightedSum(List.of(weightedValue), key -> NUM_FACTORY.numOf(0.1),
                PRECISE_DECIMAL_FACTORY);

        assertThat(weightedSum.bigDecimalValue()).isEqualByComparingTo(new BigDecimal("0.1"));
    }

    @Test
    public void decimalValueThroughDoubleTargetRoundsToPrimitiveBoundary() {
        WeightedValue<String> weightedValue = new WeightedValue<>("v", NUM_FACTORY.one());

        Num weightedSum = WeightedValue.weightedSum(List.of(weightedValue),
                key -> PRECISE_DECIMAL_FACTORY.numOf("0.12345678901234567890123456789"), NUM_FACTORY);

        assertThat(weightedSum.isZero()).isFalse();
        assertThat(weightedSum.doubleValue()).isEqualTo(0.12345678901234568d);
    }
}
