/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2017-2025 Ta4j Organization & respective
 * authors (see AUTHORS)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of
 * this software and associated documentation files (the "Software"), to deal in
 * the Software without restriction, including without limitation the rights to
 * use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 * FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 * COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 * IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
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
