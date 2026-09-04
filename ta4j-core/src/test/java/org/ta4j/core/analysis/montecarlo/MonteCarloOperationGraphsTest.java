/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.montecarlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;

import org.junit.Test;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.ShockModel;
import org.ta4j.core.indicators.forecast.MonteCarloReturnProjectionIndicator.VolatilityUpdateMode;
import org.ta4j.core.indicators.forecast.state.ReturnMoments;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Verifies the package-private canonical operation description contract: the
 * public {@link MonteCarloMethod} seam stays accelerator-neutral, equivalent
 * built-in graphs lower to a stable canonical identity, parameter/nesting
 * changes produce different identities, children encode the observable
 * concatenation order, and custom or unknown graphs decline lowering.
 */
public class MonteCarloOperationGraphsTest {

    private static final NumFactory FACTORY = DoubleNumFactory.getInstance();

    private static MonteCarloMethod shockPath() {
        return new ShockPathMonteCarloMethod(ShockModel.NORMAL, VolatilityUpdateMode.EWMA, 0.9d);
    }

    private static MonteCarloMethod normalInverseGamma() {
        return NormalInverseGammaForecastMethod.withEmpiricalPriors();
    }

    private static List<Num> window(double... values) {
        List<Num> window = new ArrayList<>(values.length);
        for (double value : values) {
            window.add(FACTORY.numOf(value));
        }
        return window;
    }

    @Test
    public void equivalentBuiltInGraphsProduceStableCanonicalIdentity() {
        MonteCarloMethod firstGraph = new EnsembleMonteCarloMethod(
                new StudentTScaleMixingMonteCarloMethod(shockPath(), 5),
                new RecentVolatilityWideningMonteCarloMethod(normalInverseGamma(), 10, 4d));
        MonteCarloMethod secondGraph = new EnsembleMonteCarloMethod(
                new StudentTScaleMixingMonteCarloMethod(shockPath(), 5),
                new RecentVolatilityWideningMonteCarloMethod(normalInverseGamma(), 10, 4d));

        MonteCarloOperation first = MonteCarloOperationGraphs.lower(firstGraph);
        MonteCarloOperation second = MonteCarloOperationGraphs.lower(secondGraph);

        assertTrue(first != null && second != null);
        assertEquals(first.canonicalId(), second.canonicalId());
        assertEquals(first, second);
    }

    @Test
    public void parameterChangesProduceDifferentIdentity() {
        MonteCarloOperation lowerDf = MonteCarloOperationGraphs
                .lower(new StudentTScaleMixingMonteCarloMethod(shockPath(), 5));
        MonteCarloOperation higherDf = MonteCarloOperationGraphs
                .lower(new StudentTScaleMixingMonteCarloMethod(shockPath(), 6));

        assertNotEquals(lowerDf.canonicalId(), higherDf.canonicalId());

        MonteCarloOperation fastDecay = MonteCarloOperationGraphs
                .lower(new ShockPathMonteCarloMethod(ShockModel.NORMAL, VolatilityUpdateMode.EWMA, 0.9d));
        MonteCarloOperation slowDecay = MonteCarloOperationGraphs
                .lower(new ShockPathMonteCarloMethod(ShockModel.NORMAL, VolatilityUpdateMode.EWMA, 0.95d));

        assertNotEquals(fastDecay.canonicalId(), slowDecay.canonicalId());

        MonteCarloOperation narrowWiden = MonteCarloOperationGraphs
                .lower(new RecentVolatilityWideningMonteCarloMethod(shockPath(), 10, 4d));
        MonteCarloOperation wideWiden = MonteCarloOperationGraphs
                .lower(new RecentVolatilityWideningMonteCarloMethod(shockPath(), 10, 8d));

        assertNotEquals(narrowWiden.canonicalId(), wideWiden.canonicalId());

        MonteCarloOperation empiricalPrior = MonteCarloOperationGraphs.lower(normalInverseGamma());
        MonteCarloOperation explicitPrior = MonteCarloOperationGraphs
                .lower(new NormalInverseGammaForecastMethod(0d, 1d, 2d, 0.01d));

        assertNotEquals(empiricalPrior.canonicalId(), explicitPrior.canonicalId());
    }

    @Test
    public void nestingAndOrderingChangesProduceDifferentIdentity() {
        MonteCarloOperation ensemble = MonteCarloOperationGraphs.lower(new EnsembleMonteCarloMethod(
                new StudentTScaleMixingMonteCarloMethod(shockPath(), 5), normalInverseGamma()));
        MonteCarloOperation reversedEnsemble = MonteCarloOperationGraphs.lower(new EnsembleMonteCarloMethod(
                normalInverseGamma(), new StudentTScaleMixingMonteCarloMethod(shockPath(), 5)));

        assertNotEquals(ensemble.canonicalId(), reversedEnsemble.canonicalId());

        MonteCarloOperation outerStudentT = MonteCarloOperationGraphs.lower(new StudentTScaleMixingMonteCarloMethod(
                new RecentVolatilityWideningMonteCarloMethod(shockPath(), 10, 4d), 5));
        MonteCarloOperation outerWidening = MonteCarloOperationGraphs.lower(
                new RecentVolatilityWideningMonteCarloMethod(new StudentTScaleMixingMonteCarloMethod(shockPath(), 5),
                        10, 4d));

        assertNotEquals(outerStudentT.canonicalId(), outerWidening.canonicalId());
    }

    @Test
    public void ensembleChildrenEncodeConcatenationOrder() {
        MonteCarloMethod ensemble = new EnsembleMonteCarloMethod(shockPath(), normalInverseGamma());

        MonteCarloOperation operation = MonteCarloOperationGraphs.lower(ensemble);

        assertTrue(operation != null);
        assertEquals(MonteCarloOperationGraphs.TYPE_ENSEMBLE, operation.type());
        assertEquals(2, operation.children().size());
        assertEquals(MonteCarloOperationGraphs.TYPE_SHOCK_PATH, operation.children().get(0).type());
        assertEquals(MonteCarloOperationGraphs.TYPE_NORMAL_INVERSE_GAMMA, operation.children().get(1).type());
    }

    @Test
    public void supportedLeavesDescribeTypeAndVersion() {
        MonteCarloOperation shockPathOperation = MonteCarloOperationGraphs.lower(shockPath());
        MonteCarloOperation normalInverseGammaOperation = MonteCarloOperationGraphs.lower(normalInverseGamma());

        assertEquals(MonteCarloOperationGraphs.TYPE_SHOCK_PATH, shockPathOperation.type());
        assertEquals(MonteCarloOperationGraphs.TYPE_NORMAL_INVERSE_GAMMA, normalInverseGammaOperation.type());
        assertEquals(MonteCarloOperationGraphs.VERSION_1, shockPathOperation.version());
        assertEquals(MonteCarloOperationGraphs.VERSION_1, normalInverseGammaOperation.version());
        assertEquals(List.of("NORMAL", "EWMA", 0.9d), shockPathOperation.parameters());
        assertEquals(List.of(0d, 0d, 0d, 0d, true), normalInverseGammaOperation.parameters());
    }

    @Test
    public void customImplementationDeclinesLowering() {
        // Lambda: fully valid on the scalar path, never lowerable.
        MonteCarloMethod customLambda = context -> {
            List<Num> samples = new ArrayList<>();
            for (int i = 0; i < context.iterationCount(); i++) {
                samples.add(context.numFactory().numOf(0.5d));
            }
            return samples;
        };

        assertNull(MonteCarloOperationGraphs.lower(customLambda));

        // Unknown decorator wrapping a supported built-in: complete semantics are
        // unknown, so the whole graph declines.
        MonteCarloMethod customDecorator = context -> shockPath().terminalReturns(context);

        assertNull(MonteCarloOperationGraphs.lower(customDecorator));

        // Unknown decorator inside a supported one: the parent declines too.
        MonteCarloMethod nestedUnknown = new StudentTScaleMixingMonteCarloMethod(customLambda, 5);

        assertNull(MonteCarloOperationGraphs.lower(nestedUnknown));
    }

    @Test
    public void customImplementationStillExecutesOnScalarPath() {
        // The lowering surface must not change the public seam: custom methods keep
        // compiling and executing through the normal scalar engine path.
        MonteCarloMethod custom = context -> {
            List<Num> samples = new ArrayList<>(context.iterationCount());
            for (int i = 0; i < context.iterationCount(); i++) {
                samples.add(context.numFactory().one());
            }
            return context.moments().isStable() ? samples : null;
        };
        ReturnMoments moments = ReturnMoments.stable(100, 3, ReturnRepresentation.LOG, FACTORY.zero(), FACTORY.zero(),
                FACTORY.one());
        MonteCarloContext context = new MonteCarloContext(100, 4, 2, window(0.01d, -0.01d, 0.01d), moments,
                new SplittableRandom(7L), FACTORY);

        List<Num> samples = custom.terminalReturns(context);

        assertEquals(2, samples.size());
        assertTrue(samples.stream().allMatch(sample -> sample.isEqual(FACTORY.one())));
    }
}
