package org.ta4j.core.analysis.criteria;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.CriterionFactory;

public class CriterionTest {

    private final CriterionFactory factory;

    public CriterionTest(CriterionFactory factory) {
        this.factory = factory;
    }

    public CriterionFactory getFactory() {
        return factory;
    }

    public <P> AnalysisCriterion getCriterion(P... params) {
        return factory.getCriterion(params);
    }

}
