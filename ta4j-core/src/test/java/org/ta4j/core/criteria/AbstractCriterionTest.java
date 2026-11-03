/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;

import org.junit.jupiter.params.ParameterizedClass;
import org.junit.jupiter.params.provider.MethodSource;
import org.ta4j.core.*;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@ParameterizedClass(name = "Test Case: {index} (1=DoubleNum, 2=DecimalNum)")
@MethodSource("function")
public abstract class AbstractCriterionTest {

    protected final NumFactory numFactory;
    protected final OpenedPositionUtils openedPositionUtils = new OpenedPositionUtils();
    private final CriterionFactory factory;

    /**
     * Constructor.
     *
     * @param factory CriterionFactory for building an AnalysisCriterion given
     *                parameters
     */
    public AbstractCriterionTest(CriterionFactory factory, NumFactory numFactory) {
        this.factory = factory;
        this.numFactory = numFactory;
    }

    public static List<NumFactory> function() {
        return List.of(DoubleNumFactory.getInstance(), DecimalNumFactory.getInstance());
    }

    /**
     * Generates an AnalysisCriterion given criterion parameters.
     *
     * @param params criterion parameters
     * @return AnalysisCriterion given parameters
     */
    public AnalysisCriterion getCriterion(Object... params) {
        return factory.getCriterion(params);
    }

    public Num numOf(Number n) {
        return numFactory.numOf(n);
    }

    public BarSeries getBarSeries(String name) {
        return new BaseBarSeriesBuilder().withNumFactory(numFactory).withName(name).build();
    }

}
