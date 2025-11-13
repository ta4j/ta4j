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
package org.ta4j.core.serialization;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferencePercentageIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;

public class IndicatorSerializationTest {

    @Test
    public void serializeIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> closePriceIndicator = new ClosePriceIndicator(series);

        String json = closePriceIndicator.toJson();
        assertEquals("{\"type\":\"ClosePriceIndicator\"}", json);
    }

    @Test
    public void serializeCompositeIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        Indicator<Num> indicator = new SMAIndicator(base, 3);

        ComponentDescriptor descriptor = indicator.toDescriptor();

        assertThat(descriptor.getType()).isEqualTo("SMAIndicator");
        assertThat(descriptor.getParameters()).containsEntry("barCount", 3);
        assertThat(descriptor.getChildren()).anySatisfy(child -> {
            assertThat(child.getLabel()).isNull();
            assertThat(child.getType()).isEqualTo("ClosePriceIndicator");
        });

        String json = indicator.toJson();
        assertEquals(
                "{\"type\":\"SMAIndicator\",\"parameters\":{\"barCount\":3},\"children\":[{\"type\":\"ClosePriceIndicator\"}]}",
                json);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deserializeRoundTrip() {
        BarSeries series = new MockBarSeriesBuilder().withData(2, 4, 6, 8, 10).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        Indicator<Num> original = new SMAIndicator(base, 2);

        String json = original.toJson();
        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, json);

        assertThat(restored).isInstanceOf(SMAIndicator.class);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    @Test
    public void trimDecimalParameters() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 1.5, 2, 3, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        Num threshold = series.numFactory().numOf("1.5000");
        DifferencePercentageIndicator indicator = new DifferencePercentageIndicator(base, threshold);

        ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("percentageThreshold", "1.5");

        String json = indicator.toJson();
        ComponentDescriptor parsed = ComponentSerialization.parse(json);
        assertThat(parsed.getParameters()).containsEntry("percentageThreshold", "1.5");

        Indicator<?> reconstructed = Indicator.fromJson(series, json);
        assertThat(reconstructed).isInstanceOf(DifferencePercentageIndicator.class);
        assertThat(((Indicator<?>) reconstructed).toDescriptor()).isEqualTo(descriptor);
    }

    @Test
    public void deserializeIndicatorWithSameTypedParameters() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7).build();
        Num accelerationStart = series.numFactory().numOf("0.03");
        Num maxAcceleration = series.numFactory().numOf("0.27");
        Num accelerationIncrement = series.numFactory().numOf("0.09");
        ParabolicSarIndicator original = new ParabolicSarIndicator(series, accelerationStart, maxAcceleration,
                accelerationIncrement);

        ComponentDescriptor descriptor = original.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("accelerationStart", "0.03");
        assertThat(descriptor.getParameters()).containsEntry("maxAcceleration", "0.27");
        assertThat(descriptor.getParameters()).containsEntry("accelerationIncrement", "0.09");

        String json = original.toJson();
        Indicator<?> reconstructed = Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(ParabolicSarIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(descriptor);
    }
}
