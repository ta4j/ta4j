/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.*;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

@RunWith(Parameterized.class)
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

    @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=DecimalNum)")
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
