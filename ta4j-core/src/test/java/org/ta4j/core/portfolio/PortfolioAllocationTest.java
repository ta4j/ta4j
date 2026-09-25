/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.portfolio;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
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
        Map<String, Num> weights = new LinkedHashMap<>();
        weights.put("ALPHA", NUM_FACTORY.numOf(0.55));
        weights.put("BETA", NUM_FACTORY.numOf(0.35));

        PortfolioAllocation allocation = new PortfolioAllocation(weights, NUM_FACTORY);

        assertEquals(List.of("ALPHA", "BETA"), List.copyOf(allocation.getTargetWeights().keySet()));
        assertNumEquals(0.55, allocation.getTargetWeight("ALPHA"));
        assertNumEquals(0.35, allocation.getTargetWeight("BETA"));
        assertNumEquals(0.10, allocation.getCashWeight());
        assertNumEquals(0, allocation.getTargetWeight("UNALLOCATED"));
    }

    @Test
    public void literalWeightsConvertExactlyAndKeepOrder() {
        Map<String, Double> weights = new LinkedHashMap<>();
        weights.put("SPY", 0.6);
        weights.put("TLT", 0.3);

        PortfolioAllocation allocation = new PortfolioAllocation(weights);

        assertEquals(List.of("SPY", "TLT"), List.copyOf(allocation.getTargetWeights().keySet()));
        assertEquals(DecimalNumFactory.getInstance().numOf("0.1"), allocation.getCashWeight());
        assertEquals("PortfolioAllocation{SPY=0.6, TLT=0.3, cash=0.1}", allocation.toString());
    }

    @Test
    public void fullyInvestedAllocationNormalizesAndCombinesDuplicateAssets() {
        PortfolioAllocation allocation = new PortfolioAllocation(
                List.of(new WeightedValue<>("ALPHA", NUM_FACTORY.two()), new WeightedValue<>("ALPHA", NUM_FACTORY.one()),
                        new WeightedValue<>("BETA", NUM_FACTORY.one())),
                NUM_FACTORY);

        assertNumEquals(0.75, allocation.getTargetWeight("ALPHA"));
        assertNumEquals(0.25, allocation.getTargetWeight("BETA"));
        assertNumEquals(0, allocation.getCashWeight());
    }

    @Test
    public void acceptsTinyWeightOvershootFromNumericDrift() {
        Map<String, Num> weights = new LinkedHashMap<>();
        weights.put("ALPHA", NUM_FACTORY.numOf(0.5));
        weights.put("BETA", NUM_FACTORY.numOf(0.5).plus(NUM_FACTORY.epsilon().dividedBy(NUM_FACTORY.two())));

        PortfolioAllocation allocation = new PortfolioAllocation(weights, NUM_FACTORY);

        assertNumEquals(1, allocation.getTotalWeight());
        assertNumEquals(0, allocation.getCashWeight());
    }

    @Test
    public void rejectsInvalidWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioAllocation(Map.of("ALPHA", 0.8, "BETA", 0.4)));
        assertThrows(IllegalArgumentException.class, () -> new PortfolioAllocation(Map.of("ALPHA", -0.1)));
        assertThrows(IllegalArgumentException.class, () -> new PortfolioAllocation(Map.of("ALPHA", Double.NaN)));
        assertThrows(IllegalArgumentException.class, () -> new PortfolioAllocation(Map.of(" ", 0.5)));
        assertThrows(IllegalArgumentException.class, () -> new PortfolioAllocation(Map.<String, Double>of()));
    }

    @Test
    public void finiteHighPrecisionWeightUsesLeverageValidation() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        Map<String, Num> weights = Map.of("ALPHA", decimalFactory.numOf("1E400"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PortfolioAllocation(weights, decimalFactory));

        assertTrue(exception.getMessage().startsWith("sum of target weights must be <= 1"));
    }
}
