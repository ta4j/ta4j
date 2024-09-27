/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2024 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package org.ta4j.core.criteria;

import java.util.List;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.CriterionFactory;
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

}
