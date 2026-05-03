/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.OmegaRatioCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
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
    public void nonDefaultCriterionStateRoundTripsThroughCanonicalJson() {
        AnalysisCriterion original = new NumberOfPositionsCriterion(false);

        String json = original.toJson();
        AnalysisCriterion restored = AnalysisCriterion.fromJson(json);

        assertThat(json).isEqualTo("{\"type\":\"NumberOfPositionsCriterion\",\"parameters\":{\"lessIsBetter\":false}}");
        assertThat(restored.toJson()).isEqualTo(json);
    }

    @Test
    public void nonDefaultCriterionStateDoesNotRenderAsDefaultAlias() {
        AnalysisCriterion criterion = new NumberOfPositionsCriterion(false);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, criterion::toExpression);

        assertThat(exception).hasMessageContaining("No named analysis criterion shorthand registered");
    }

    @Test
    public void customNumericCriterionStateRoundTripsThroughCanonicalJson() {
        AnalysisCriterion original = new SharpeRatioCriterion(0.05);

        String json = original.toJson();
        AnalysisCriterion restored = AnalysisCriterion.fromJson(json);

        assertThat(json).isEqualTo("{\"type\":\"SharpeRatioCriterion\",\"parameters\":{\"annualRiskFreeRate\":0.05}}");
        assertThat(restored.toJson()).isEqualTo(json);
    }

    @Test
    public void constructorParameterCanMapToUniqueFieldOfSameType() {
        AnalysisCriterion original = new GrossReturnCriterion(ReturnRepresentation.PERCENTAGE);

        String json = original.toJson();
        AnalysisCriterion restored = AnalysisCriterion.fromJson(json);

        assertThat(json)
                .isEqualTo("{\"type\":\"GrossReturnCriterion\",\"parameters\":{\"representation\":\"PERCENTAGE\"}}");
        assertThat(restored.toJson()).isEqualTo(json);
    }

    @Test
    public void overloadedCriterionStateUsesConstructorThatPreservesAllChangedFields() {
        AnalysisCriterion original = new OmegaRatioCriterion(ReturnRepresentation.PERCENTAGE, 0.05,
                EquityCurveMode.REALIZED, OpenPositionHandling.IGNORE);

        String json = original.toJson();
        AnalysisCriterion restored = AnalysisCriterion.fromJson(json);

        assertThat(json).isEqualTo(
                "{\"type\":\"OmegaRatioCriterion\",\"parameters\":{\"returnRepresentation\":\"PERCENTAGE\",\"threshold\":0.05,\"equityCurveMode\":\"REALIZED\",\"openPositionHandling\":\"IGNORE\"}}");
        assertThat(restored.toJson()).isEqualTo(json);
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
