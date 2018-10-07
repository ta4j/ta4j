package org.ta4j.core.analysis.criteria;

import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.CriterionFactory;
import org.ta4j.core.num.DoubleNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.PrecisionNum;

import java.util.Arrays;
import java.util.List;
import java.util.function.Function;

@RunWith(Parameterized.class)
public abstract class AbstractCriterionTest {

    private final CriterionFactory factory;
    protected final Function<Number, Num> numFunction;

    @Parameterized.Parameters(name = "Test Case: {index} (0=DoubleNum, 1=PrecisionNum)")
    public static List<Function<Number, Num>> function(){
        return Arrays.asList(DoubleNum::valueOf, PrecisionNum::valueOf);
    }
    /**
     * Constructor.
     * 
     * @param factory CriterionFactory for building an AnalysisCriterion given
     *            parameters
     */
    public AbstractCriterionTest(CriterionFactory factory, Function<Number, Num> numFunction) {
        this.factory = factory;
        this.numFunction = numFunction;
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

    public Num numOf(Number n){
        return numFunction.apply(n);
    }

}
