/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.lppl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

        assertThatThrownBy(() -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION,
                one, one, fit, fits, 0, 1, 1, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionableFits")
                .hasMessageContaining("attemptedFits");
        assertThatThrownBy(() -> new LPPLExhaustion(LPPLExhaustionStatus.VALID, LPPLExhaustionSide.CRASH_EXHAUSTION,
                one, one, fit, fits, 1, 1, 0, 0)).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("crashFits + bubbleFits");
    }
}
