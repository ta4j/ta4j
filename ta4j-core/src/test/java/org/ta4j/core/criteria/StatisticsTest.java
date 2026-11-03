/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import static org.ta4j.core.TestUtils.assertNumEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@ParameterizedClass(name = "NumFactory: {index} (1=DoubleNum, 2=DecimalNum)")
@MethodSource("function")
public class StatisticsTest {

    private final NumFactory numFactory;

    public StatisticsTest(NumFactory numFactory) {
        this.numFactory = numFactory;
    }

    public static List<NumFactory> function() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    @Test
    public void calculateReturnsZeroForEmptyValues() {
        var result = Statistics.MEAN.calculate(numFactory, new Num[0]);

        assertNumEquals(numFactory.zero(), result);
    }

    @Test
    public void calculateMeanFromNumValues() {
        var values = new Num[] { numFactory.one(), numFactory.two(), numFactory.three() };

        assertNumEquals(numFactory.two(), Statistics.MEAN.calculate(numFactory, values));
    }

    @Test
    public void calculateMedianAndPercentilesFromNumValues() {
        var values = new Num[] { numFactory.one(), numFactory.two(), numFactory.three(), numFactory.numOf(4) };

        assertNumEquals(numFactory.two(), Statistics.MEDIAN.calculate(numFactory, values));
        assertNumEquals(numFactory.numOf(4), Statistics.P95.calculate(numFactory, values));
        assertNumEquals(numFactory.numOf(4), Statistics.P99.calculate(numFactory, values));
    }

    @Test
    public void calculateMinAndMaxFromNumValues() {
        var values = new Num[] { numFactory.three(), numFactory.one(), numFactory.two() };

        assertNumEquals(numFactory.one(), Statistics.MIN.calculate(numFactory, values));
        assertNumEquals(numFactory.three(), Statistics.MAX.calculate(numFactory, values));
    }
}
