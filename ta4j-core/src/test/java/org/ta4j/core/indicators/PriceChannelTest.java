/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

class PriceChannelTest {

    @Test
    void boundaryLabelsAreStable() {
        assertThat(PriceChannel.Boundary.UPPER.label()).isEqualTo("upper");
        assertThat(PriceChannel.Boundary.LOWER.label()).isEqualTo("lower");
        assertThat(PriceChannel.Boundary.MEDIAN.label()).isEqualTo("median");
    }

    @Test
    void defaultHelpersRespectChannelValidityAndTolerance() {
        assertChannelHelpers(DoubleNumFactory.getInstance());
        assertChannelHelpers(DecimalNumFactory.getInstance());
    }

    private void assertChannelHelpers(NumFactory factory) {
        Num upper = factory.numOf(110);
        Num lower = factory.numOf(90);
        Num median = factory.numOf(100);
        Num priceInside = factory.numOf(95);
        Num priceOutside = factory.numOf(88);
        Num tolerance = factory.numOf(2);

        PriceChannel channel = new TestChannel(upper, lower, median);

        assertThat(channel.isValid()).isTrue();
        assertThat(channel.width()).isEqualByComparingTo(factory.numOf(20));
        assertThat(channel.contains(priceInside, null)).isTrue();
        assertThat(channel.contains(lower, factory.zero())).isTrue();
        assertThat(channel.contains(upper, factory.zero())).isTrue();
        assertThat(channel.contains(priceOutside, tolerance)).isTrue();
        assertThat(channel.contains(factory.numOf(87), tolerance)).isFalse();

        PriceChannel invalidUpper = new TestChannel(NaN.NaN, lower, median);
        assertThat(invalidUpper.isValid()).isFalse();
        assertThat(Num.isNaNOrNull(invalidUpper.width())).isTrue();
        assertThat(invalidUpper.contains(priceInside, tolerance)).isFalse();

        PriceChannel invalidLower = new TestChannel(upper, NaN.NaN, median);
        assertThat(invalidLower.isValid()).isFalse();
        assertThat(Num.isNaNOrNull(invalidLower.width())).isTrue();
        assertThat(invalidLower.contains(priceInside, tolerance)).isFalse();

        assertThat(channel.contains(NaN.NaN, tolerance)).isFalse();
        assertThat(channel.contains(priceInside, NaN.NaN)).isFalse();
    }

    private record TestChannel(Num upper, Num lower, Num median) implements PriceChannel {
    }
}
