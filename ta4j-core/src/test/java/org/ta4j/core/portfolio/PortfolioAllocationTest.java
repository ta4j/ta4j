/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.analysis.WeightedValue;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

public class PortfolioAllocationTest {

    private static final NumFactory NUM_FACTORY = DoubleNumFactory.getInstance();

    @Test
    public void targetWeightsCanLeaveCashUnallocated() {
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        Map<PortfolioAsset, Num> weights = new LinkedHashMap<>();
        weights.put(alpha, NUM_FACTORY.numOf(0.55));
        weights.put(beta, NUM_FACTORY.numOf(0.35));

        PortfolioAllocation allocation = PortfolioAllocation.targetWeights(weights, NUM_FACTORY);

        assertNumEquals(0.55, allocation.targetWeight(alpha));
        assertNumEquals(0.35, allocation.targetWeight(beta));
        assertNumEquals(0.10, allocation.cashWeight());
        assertNumEquals(0, allocation.targetWeight(PortfolioAsset.of("UNALLOCATED")));
    }

    @Test
    public void fullyInvestedAllocationReusesWeightedValueNormalization() {
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");

        PortfolioAllocation allocation = PortfolioAllocation.fullyInvested(
                List.of(new WeightedValue<>(alpha, NUM_FACTORY.two()), new WeightedValue<>(beta, NUM_FACTORY.one())),
                NUM_FACTORY);

        assertNumEquals(NUM_FACTORY.numOf(2d / 3d), allocation.targetWeight(alpha), 0.0001);
        assertNumEquals(NUM_FACTORY.numOf(1d / 3d), allocation.targetWeight(beta), 0.0001);
        assertNumEquals(0, allocation.cashWeight());
    }

    @Test
    public void fullyInvestedAllocationCombinesDuplicateAssets() {
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");

        PortfolioAllocation allocation = PortfolioAllocation.fullyInvested(
                List.of(new WeightedValue<>(alpha, NUM_FACTORY.two()), new WeightedValue<>(alpha, NUM_FACTORY.one()),
                        new WeightedValue<>(beta, NUM_FACTORY.one())),
                NUM_FACTORY);

        assertNumEquals(0.75, allocation.targetWeight(alpha));
        assertNumEquals(0.25, allocation.targetWeight(beta));
        assertNumEquals(0, allocation.cashWeight());
    }

    @Test
    public void acceptsTinyWeightOvershootFromNumericDrift() {
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");
        Map<PortfolioAsset, Num> weights = new LinkedHashMap<>();
        weights.put(alpha, NUM_FACTORY.numOf(0.5));
        weights.put(beta, NUM_FACTORY.numOf(0.5).plus(NUM_FACTORY.epsilon().dividedBy(NUM_FACTORY.two())));

        PortfolioAllocation allocation = PortfolioAllocation.targetWeights(weights, NUM_FACTORY);

        assertNumEquals(1, allocation.totalWeight());
        assertNumEquals(0, allocation.cashWeight());
    }

    @Test
    public void rejectsLeveragedWeights() {
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        PortfolioAsset beta = PortfolioAsset.of("BETA");

        assertThrows(IllegalArgumentException.class, () -> PortfolioAllocation
                .targetWeights(Map.of(alpha, NUM_FACTORY.numOf(0.8), beta, NUM_FACTORY.numOf(0.4)), NUM_FACTORY));
    }

    @Test
    public void finiteHighPrecisionWeightUsesLeverageValidation() {
        PortfolioAsset alpha = PortfolioAsset.of("ALPHA");
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        Map<PortfolioAsset, Num> weights = Map.of(alpha, decimalFactory.numOf("1E400"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> PortfolioAllocation.targetWeights(weights, decimalFactory));

        assertEquals("sum of target weights must be <= 1", exception.getMessage());
    }
}
