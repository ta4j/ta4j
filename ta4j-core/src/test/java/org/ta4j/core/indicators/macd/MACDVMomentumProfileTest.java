/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.macd;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import org.ta4j.core.indicators.AbstractIndicatorTest;
import org.ta4j.core.num.NumFactory;

public class MACDVMomentumProfileTest extends AbstractIndicatorTest<Object, Object> {

    public MACDVMomentumProfileTest(NumFactory numFactory) {
        super(numFactory);
    }

    @Test
    public void defaultProfileClassifiesMomentumStates() {
        MACDVMomentumProfile profile = MACDVMomentumProfile.defaultProfile();

        assertThat(profile.classify(null)).isEqualTo(MACDVMomentumState.UNDEFINED);
        assertThat(profile.classify(numFactory.numOf(151))).isEqualTo(MACDVMomentumState.HIGH_RISK);
        assertThat(profile.classify(numFactory.numOf(150))).isEqualTo(MACDVMomentumState.RALLYING_OR_RETRACING);
        assertThat(profile.classify(numFactory.numOf(0))).isEqualTo(MACDVMomentumState.RANGING);
        assertThat(profile.classify(numFactory.numOf(-150))).isEqualTo(MACDVMomentumState.REBOUNDING_OR_REVERSING);
        assertThat(profile.classify(numFactory.numOf(-149))).isEqualTo(MACDVMomentumState.REBOUNDING_OR_REVERSING);
        assertThat(profile.classify(numFactory.numOf(-151))).isEqualTo(MACDVMomentumState.LOW_RISK);
    }

    @Test
    public void customProfileClassifiesMomentumStates() {
        MACDVMomentumProfile profile = new MACDVMomentumProfile(10, 20, -10, -20);

        assertThat(profile.classify(numFactory.numOf(21))).isEqualTo(MACDVMomentumState.HIGH_RISK);
        assertThat(profile.classify(numFactory.numOf(10))).isEqualTo(MACDVMomentumState.RALLYING_OR_RETRACING);
        assertThat(profile.classify(numFactory.numOf(0))).isEqualTo(MACDVMomentumState.RANGING);
        assertThat(profile.classify(numFactory.numOf(-20))).isEqualTo(MACDVMomentumState.REBOUNDING_OR_REVERSING);
        assertThat(profile.classify(numFactory.numOf(-19))).isEqualTo(MACDVMomentumState.REBOUNDING_OR_REVERSING);
        assertThat(profile.classify(numFactory.numOf(-21))).isEqualTo(MACDVMomentumState.LOW_RISK);
    }

    @Test
    public void rejectsInvalidThresholdConfiguration() {
        assertThrows(NullPointerException.class, () -> new MACDVMomentumProfile(null, 20, -10, -20));
        assertThrows(IllegalArgumentException.class, () -> new MACDVMomentumProfile(-1, 20, -10, -20));
        assertThrows(IllegalArgumentException.class, () -> new MACDVMomentumProfile(10, 10, -10, -20));
        assertThrows(IllegalArgumentException.class, () -> new MACDVMomentumProfile(10, 20, 1, -20));
        assertThrows(IllegalArgumentException.class, () -> new MACDVMomentumProfile(10, 20, -10, -10));
        assertThrows(IllegalArgumentException.class, () -> new MACDVMomentumProfile(Double.NaN, 20, -10, -20));
    }
}
