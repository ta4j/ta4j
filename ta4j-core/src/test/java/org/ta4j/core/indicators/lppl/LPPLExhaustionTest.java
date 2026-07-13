/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;

class LPPLExhaustionTest {

    @Test
    void actionabilityRequiresAnActionableDirectionalFit() {
        Num one = DoubleNumFactory.getInstance().one();
        LPPLFit fit = LPPLFitTest.validFit(0.03);
        LPPLExhaustion exhaustion = new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION,
                one, one, fit, List.of(fit), 1, 1, 1, 0);

        assertThat(exhaustion.isActionable()).isTrue();
        assertThat(exhaustion.actionableFits()).isEqualTo(1);
    }

    @Test
    void rejectsInconsistentActionableFitCounts() {
        Num one = DoubleNumFactory.getInstance().one();
        LPPLFit fit = LPPLFitTest.validFit(0.03);
        List<LPPLFit> fits = List.of(fit);

        IllegalArgumentException attemptedFitsFailure = assertThrows(IllegalArgumentException.class,
                () -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION, one, one, fit,
                        fits, 0, 1, 1, 0));
        IllegalArgumentException directionalFitsFailure = assertThrows(IllegalArgumentException.class,
                () -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION, one, one, fit,
                        fits, 1, 1, 0, 0));

        assertThat(attemptedFitsFailure).hasMessageContaining("attemptedFits");
        assertThat(directionalFitsFailure).hasMessageContaining("crashFits + bubbleFits");
    }

    @Test
    void rejectsContradictoryFitAndScoreMetadata() {
        Num one = DoubleNumFactory.getInstance().one();
        Num negativeOne = DoubleNumFactory.getInstance().minusOne();
        Num two = DoubleNumFactory.getInstance().two();
        LPPLFit fit = LPPLFitTest.validFit(0.03);

        assertThrows(IllegalArgumentException.class, () -> new LPPLExhaustion(LPPLExhaustionStatus.VALID,
                LPPLExhaustionSide.CRASH_EXHAUSTION, one, one, fit, List.of(fit), 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new LPPLExhaustion(LPPLExhaustionStatus.VALID,
                LPPLExhaustionSide.CRASH_EXHAUSTION, negativeOne, one, fit, List.of(fit), 1, 1, 1, 0));
        assertThrows(IllegalArgumentException.class, () -> new LPPLExhaustion(LPPLExhaustionStatus.VALID,
                LPPLExhaustionSide.CRASH_EXHAUSTION, two, one, fit, List.of(fit), 1, 1, 1, 0));
    }

    @Test
    void rejectsSideThatContradictsActionableFitMajority() {
        Num zero = DoubleNumFactory.getInstance().zero();
        Num one = DoubleNumFactory.getInstance().one();
        LPPLFit fit = LPPLFitTest.validFit(0.03);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.NONE, zero, one, fit,
                        List.of(fit), 1, 1, 1, 0));

        assertThat(failure).hasMessageContaining("crashFits/bubbleFits majority");
    }
}
