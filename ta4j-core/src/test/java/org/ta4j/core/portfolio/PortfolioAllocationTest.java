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
import org.ta4j.core.num.DecimalNum;
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

        assertNumEquals(0.55, allocation.getTargetWeight("ALPHA"));
        assertNumEquals(0.35, allocation.getTargetWeight("BETA"));
        assertNumEquals(0.10, allocation.getCashWeight());
        assertNumEquals(0, allocation.getTargetWeight("UNALLOCATED"));
    }

    @Test
    public void fullyInvestedAllocationReusesWeightedValueNormalization() {
        PortfolioAllocation allocation = new PortfolioAllocation(List
                .of(new WeightedValue<>("ALPHA", NUM_FACTORY.two()), new WeightedValue<>("BETA", NUM_FACTORY.one())),
                NUM_FACTORY);

        assertNumEquals(NUM_FACTORY.numOf(2d / 3d), allocation.getTargetWeight("ALPHA"), 0.0001);
        assertNumEquals(NUM_FACTORY.numOf(1d / 3d), allocation.getTargetWeight("BETA"), 0.0001);
        assertNumEquals(0, allocation.getCashWeight());
    }

    @Test
    public void fullyInvestedAllocationCombinesDuplicateAssets() {
        PortfolioAllocation allocation = new PortfolioAllocation(List.of(
                new WeightedValue<>("ALPHA", NUM_FACTORY.two()), new WeightedValue<>("ALPHA", NUM_FACTORY.one()),
                new WeightedValue<>("BETA", NUM_FACTORY.one())), NUM_FACTORY);

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
    public void rejectsLeveragedWeights() {
        assertThrows(IllegalArgumentException.class,
                () -> new PortfolioAllocation(Map.of("ALPHA", NUM_FACTORY.numOf(0.8), "BETA", NUM_FACTORY.numOf(0.4)),
                        NUM_FACTORY));
    }

    @Test
    public void finiteHighPrecisionWeightUsesLeverageValidation() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance();
        Map<String, Num> weights = Map.of("ALPHA", decimalFactory.numOf("1E400"));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new PortfolioAllocation(weights, decimalFactory));

        assertEquals("sum of target weights must be <= 1", exception.getMessage());
    }

    @Test
    public void normalizesFiniteHighPrecisionWeightedValues() {
        NumFactory decimalFactory = DecimalNumFactory.getInstance();

        PortfolioAllocation allocation = new PortfolioAllocation(
                List.of(new WeightedValue<>("ALPHA", decimalFactory.numOf("1E400")),
                        new WeightedValue<>("BETA", decimalFactory.numOf("2E400"))),
                decimalFactory);

        assertNumEquals(decimalFactory.numOf("0.3333333333333333"), allocation.getTargetWeight("ALPHA"), 0.0001);
        assertNumEquals(decimalFactory.numOf("0.6666666666666667"), allocation.getTargetWeight("BETA"), 0.0001);
    }

    @Test
    public void normalizesSameClassWeightsToRequestedFactoryPrecision() {
        NumFactory targetFactory = DecimalNumFactory.getInstance(3);
        NumFactory sourceFactory = DecimalNumFactory.getInstance(40);

        PortfolioAllocation allocation = new PortfolioAllocation(
                Map.of("ALPHA", sourceFactory.numOf("0.123456"), "BETA", sourceFactory.numOf("0.5")), targetFactory);

        Num alphaWeight = allocation.getTargetWeight("ALPHA");
        assertNumEquals(targetFactory.numOf("0.123"), alphaWeight);
        assertEquals(3, ((DecimalNum) alphaWeight).getMathContext().getPrecision());
    }
}
