/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;

public class AnalysisCriterionSerializationTest {

    @Test
    public void expressionRestoresBuiltInCriterionAlias() {
        AnalysisCriterion criterion = AnalysisCriterion.fromExpression("NetProfit");

        assertThat(criterion).isInstanceOf(NetProfitCriterion.class);
    }

    @Test
    public void expressionFallsBackToFullyQualifiedCriterionClassName() {
        AnalysisCriterion criterion = AnalysisCriterion.fromExpression("org.ta4j.core.criteria.pnl.NetProfitCriterion");

        assertThat(criterion).isInstanceOf(NetProfitCriterion.class);
    }

    @Test
    public void criterionRoundTripsThroughCanonicalJson() {
        AnalysisCriterion original = new SharpeRatioCriterion();

        String json = original.toJson();
        AnalysisCriterion restored = AnalysisCriterion.fromJson(json);

        assertThat(json).isEqualTo("{\"type\":\"SharpeRatioCriterion\"}");
        assertThat(restored).isInstanceOf(SharpeRatioCriterion.class);
    }

    @Test
    public void criterionRendersCompactExpression() {
        AnalysisCriterion criterion = new NetProfitCriterion();

        assertThat(criterion.toExpression()).isEqualTo("NetProfit");
    }

    @Test
    public void invalidCriterionAliasThrowsUsefulError() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AnalysisCriterion.fromExpression("DefinitelyMissing"));

        assertThat(exception).hasMessageContaining("Unknown analysis criterion type")
                .hasMessageContaining("DefinitelyMissing");
    }
}
