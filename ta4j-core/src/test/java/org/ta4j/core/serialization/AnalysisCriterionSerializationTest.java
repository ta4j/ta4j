/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import java.time.Duration;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.EquityCurveMode;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.criteria.NumberOfPositionsCriterion;
import org.ta4j.core.criteria.OmegaRatioCriterion;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.criteria.SharpeRatioCriterion;
import org.ta4j.core.criteria.custom.CustomPackageCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.criteria.pnl.NetProfitCriterion;
import org.ta4j.core.num.Num;

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

    @Test
    public void criterionInUnlistedCriteriaSubpackageUsesFullyQualifiedName() {
        AnalysisCriterion original = new CustomPackageCriterion();

        String json = original.toJson();
        AnalysisCriterion restored = AnalysisCriterion.fromJson(json);

        assertThat(json).contains(CustomPackageCriterion.class.getName());
        assertThat(restored).isInstanceOf(CustomPackageCriterion.class);
    }

    @Test
    public void unsupportedDefaultStateDoesNotSerializeAsDefault() {
        AnalysisCriterion criterion = new DurationBackedCriterion(Duration.ofDays(2));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, criterion::toJson);

        assertThat(exception).hasMessageContaining("state cannot be serialized safely");
    }

    @Test
    public void rejectedCriterionClassDoesNotRunStaticInitializer() {
        InitializerProbe.initialized = false;

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> AnalysisCriterion.fromExpression(RejectedCriterionProbe.class.getName()));

        assertThat(exception).hasMessageContaining("Unknown analysis criterion type")
                .hasMessageContaining(RejectedCriterionProbe.class.getName());
        assertThat(InitializerProbe.initialized).isFalse();
    }

    private static final class DurationBackedCriterion implements AnalysisCriterion {

        private final Duration duration;

        private DurationBackedCriterion() {
            this(Duration.ofDays(1));
        }

        private DurationBackedCriterion(Duration duration) {
            this.duration = duration;
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return series.numFactory().zero();
        }

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            return series.numFactory().zero();
        }

        @Override
        public boolean betterThan(Num criterionValue1, Num criterionValue2) {
            return criterionValue1.isGreaterThan(criterionValue2);
        }
    }

    private static final class InitializerProbe {

        private static boolean initialized;
    }

    private static final class RejectedCriterionProbe {

        static {
            InitializerProbe.initialized = true;
        }
    }
}
