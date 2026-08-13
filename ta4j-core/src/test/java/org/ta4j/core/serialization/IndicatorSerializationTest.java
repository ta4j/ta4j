/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.util.List;
import java.util.Map;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Indicator;
import org.ta4j.core.analysis.InvestedInterval;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.ChopIndicator;
import org.ta4j.core.indicators.IndicatorConstructorSelectionTestIndicator;
import org.ta4j.core.indicators.KalmanFilterIndicator;
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
import org.ta4j.core.indicators.macd.MACDVIndicator;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.NaN;
import org.ta4j.core.num.Num;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class IndicatorSerializationTest {
    @Test
    public void toJsonRejectsUnresolvableIndicatorType() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Boolean> indicator = new InvestedInterval(series, new BaseTradingRecord());

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                indicator::toJson);
        assertThat(exception.getMessage()).contains("Unknown indicator type: InvestedInterval");
    }

    @Test
    public void toJsonRejectsNonFiniteNumericParameter() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Num nan = NaN.NaN;
        Indicator<Num> indicator = new FixedIndicator<>(series, nan, series.numFactory().numOf(2));

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                indicator::toJson);
        assertThat(exception.getMessage()).contains("Non-finite numeric parameter");
    }

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
        assertThat(descriptor.getComponents()).hasSize(1).anySatisfy(child -> {
            assertThat(child.getLabel()).isNull();
            assertThat(child.getType()).isEqualTo("ClosePriceIndicator");
        });

        String json = indicator.toJson();
        assertEquals(
                "{\"type\":\"SMAIndicator\",\"parameters\":{\"barCount\":3},\"components\":[{\"type\":\"ClosePriceIndicator\"}]}",
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
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());

        assertThat(restored).isInstanceOf(SMAIndicator.class);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(original.getValue(i));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    public void deserializeMacdIndicatorPrefersNonDeprecatedTypeWhenSimpleNameCollides() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20).build();
        Indicator<Num> price = new ClosePriceIndicator(series);
        Indicator<Num> original = new MACDVIndicator(price, 12, 26, 9);

        Indicator<Num> restored = (Indicator<Num>) Indicator.fromJson(series, original.toJson());

        assertThat(restored).isInstanceOf(MACDVIndicator.class);
        assertThat(restored.getClass().isAnnotationPresent(Deprecated.class)).isFalse();
        assertThat(restored.toDescriptor()).isEqualTo(original.toDescriptor());
    }

    @Test
    public void serializeBooleanFixedIndicator() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        FixedIndicator<Boolean> indicator = new FixedIndicator<>(series, true, false, true);

        ComponentDescriptor descriptor = indicator.toDescriptor();
        assertThat(descriptor.getParameters()).containsEntry("values", List.of(true, false, true));

        Indicator<?> restored = IndicatorSerialization.fromDescriptor(series, descriptor);
        assertThat(restored).isInstanceOf(FixedIndicator.class);
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(restored.getValue(i)).isEqualTo(indicator.getValue(i));
        }

        Indicator<?> fromJson = IndicatorSerialization.fromJson(series, indicator.toJson());
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(fromJson.getValue(i)).isEqualTo(indicator.getValue(i));
        }
    }

    @Test
    public void describeRejectsNullIndicator() {
        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                () -> IndicatorSerialization.describe(null));

        assertThat(exception).hasMessage("Indicator cannot be null").hasNoCause();
    }

    @Test
    public void fromDescriptorRejectsNullInputs() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        ComponentDescriptor descriptor = ComponentDescriptor.builder().withType("ClosePriceIndicator").build();

        IndicatorSerializationException nullSeriesException = assertThrows(IndicatorSerializationException.class,
                () -> IndicatorSerialization.fromDescriptor(null, descriptor));
        assertThat(nullSeriesException).hasMessage("Series and descriptor cannot be null").hasNoCause();

        IndicatorSerializationException nullDescriptorException = assertThrows(IndicatorSerializationException.class,
                () -> IndicatorSerialization.fromDescriptor(series, null));
        assertThat(nullDescriptorException).hasMessage("Series and descriptor cannot be null").hasNoCause();
    }

    @Test
    public void fromJsonRejectsMalformedJsonSyntax() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        String json = "{\"type\":\"SMAIndicator\"";

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                () -> Indicator.fromJson(series, json));

        assertThat(exception).hasMessage("Failed to deserialize indicator from JSON")
                .hasCauseInstanceOf(com.google.gson.JsonParseException.class);
    }

    @Test
    public void fromJsonRejectsFractionalIntegerParameter() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        String json = """
                {"type":"SMAIndicator","parameters":{"barCount":1.9},"components":[{"type":"ClosePriceIndicator"}]}""";

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                () -> Indicator.fromJson(series, json));

        assertThat(exception).hasMessageContaining("no suitable constructor");
    }

    @Test
    public void fromJsonRejectsOverflowingIntegerParameter() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3).build();
        String json = """
                {"type":"SMAIndicator","parameters":{"barCount":2147483648},"components":[{"type":"ClosePriceIndicator"}]}""";

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                () -> Indicator.fromJson(series, json));

        assertThat(exception).hasMessageContaining("no suitable constructor");
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

    @Test
    public void deserializePrefersConstructorThatConsumesAllComponents() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        Indicator<Num> extra = new SMAIndicator(base, 2);
        Num scale = series.numFactory().numOf("1.5");
        Num offset = series.numFactory().numOf("2.5");
        Num bias = series.numFactory().numOf("0.75");

        IndicatorConstructorSelectionTestIndicator original = new IndicatorConstructorSelectionTestIndicator(base,
                extra, scale, offset, bias);

        String json = original.toJson();
        Indicator<?> reconstructed = Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(IndicatorConstructorSelectionTestIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(original.toDescriptor());
    }

    @SuppressWarnings("deprecation")
    @Test
    public void deserializeSkipsSameArityConstructorWithIncompatibleEnumParameter() {
        BarSeries series = new MockBarSeriesBuilder().withData(10, 11, 9, 12, 10, 13, 11, 14).build();
        ChopIndicator original = new ChopIndicator(series, 4, 37);

        Indicator<?> reconstructed = Indicator.fromJson(series, original.toJson());

        assertThat(reconstructed).isInstanceOf(ChopIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(original.toDescriptor());
    }

    @Test
    public void deserializeRejectsDescriptorsWithUnconsumedComponentsOrParameters() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        ComponentDescriptor extraComponentDescriptor = ComponentDescriptor.builder()
                .withType("ClosePriceIndicator")
                .addComponent(ComponentDescriptor.builder().withType("ClosePriceIndicator").build())
                .build();

        IndicatorSerializationException componentException = assertThrows(IndicatorSerializationException.class,
                () -> IndicatorSerialization.fromDescriptor(series, extraComponentDescriptor));
        assertThat(componentException).hasMessageContaining("no suitable constructor");

        ComponentDescriptor extraParameterDescriptor = ComponentDescriptor.builder()
                .withType("ClosePriceIndicator")
                .withParameters(Map.of("unusedBarCount", 3))
                .build();

        IndicatorSerializationException parameterException = assertThrows(IndicatorSerializationException.class,
                () -> IndicatorSerialization.fromDescriptor(series, extraParameterDescriptor));
        assertThat(parameterException).hasMessageContaining("no suitable constructor");
    }

    @Test
    public void transientFieldsNotSerialized() {
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5, 6, 7, 8, 9, 10).build();
        Indicator<Num> base = new ClosePriceIndicator(series);
        KalmanFilterIndicator indicator = new KalmanFilterIndicator(base, 1e-4, 1e-3);

        // Use the indicator to populate stateful fields (filter and lastProcessedIndex)
        indicator.getValue(series.getEndIndex());

        ComponentDescriptor descriptor = indicator.toDescriptor();

        // Verify the indicator type is correct
        assertThat(descriptor.getType()).isEqualTo("KalmanFilterIndicator");

        // Verify that transient stateful fields are NOT serialized
        assertThat(descriptor.getParameters()).doesNotContainKey("lastProcessedIndex");
        assertThat(descriptor.getParameters()).doesNotContainKey("filter");
        assertThat(descriptor.getParameters()).doesNotContainKey("stateIndicator");

        // Dynamic noise is now part of the constructor graph rather than mutable
        // filter state. Constant-noise constructors serialize through the same graph.
        assertThat(descriptor.getParameters()).isEmpty();
        assertThat(descriptor.getComponents()).hasSize(3);
        assertThat(descriptor.getComponents().get(1).getType()).isEqualTo("KalmanNoiseIndicator");
        assertThat(descriptor.getComponents().get(2).getType()).isEqualTo("KalmanNoiseIndicator");

        // Verify round-trip deserialization works
        String json = indicator.toJson();
        Indicator<?> reconstructed = Indicator.fromJson(series, json);

        assertThat(reconstructed).isInstanceOf(KalmanFilterIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(descriptor);

        // Verify the reconstructed indicator produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(reconstructed.getValue(i)).isEqualTo(indicator.getValue(i));
        }
    }

    @Test
    public void serializeCircularIndicatorReferenceFailsLoudForUnresolvableType() {
        // Test-local indicator types cannot be resolved on deserialization. Per the
        // fail-fast serialization contract, toDescriptor must reject the type before
        // traversal instead of emitting JSON that can never be read back.
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);

        CircularTestIndicator indicatorA = new CircularTestIndicator(series, base, "A", 5);
        CircularTestIndicator indicatorB = new CircularTestIndicator(series, base, "B", 10);
        indicatorA.setReferencedIndicator(indicatorB);
        indicatorB.setReferencedIndicator(indicatorA);

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                indicatorA::toDescriptor);
        assertThat(exception.getMessage()).contains("Unknown indicator type: CircularTestIndicator");
    }

    @Test
    public void serializeSelfReferencingIndicatorFailsLoudForUnresolvableType() {
        // Same fail-fast contract as above: a self-referencing test-local indicator
        // is rejected before traversal. The visited-placeholder mechanism remains
        // defensive for resolvable indicator graphs (first-party indicators cannot
        // form cycles since children are fixed at construction).
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);

        CircularTestIndicator indicator = new CircularTestIndicator(series, base, "SelfRef", 7);
        indicator.setReferencedIndicator(indicator);

        IndicatorSerializationException exception = assertThrows(IndicatorSerializationException.class,
                indicator::toJson);
        assertThat(exception.getMessage()).contains("Unknown indicator type: CircularTestIndicator");
    }

    @Test
    public void deserializeIndicatorWithNoComponents() {
        // Test that indicators with no child components can be deserialized correctly.
        // This exercises the code path where components is an empty list, which
        // exercises
        // similar defensive logic to the null check in tryInvoke. The null check in
        // tryInvoke
        // is defensive programming that protects against a bug or future change, but in
        // normal
        // operation components is always initialized as an empty list (never null) when
        // there
        // are no child indicators. Since there's no way to pass null components through
        // the
        // public API (instantiate always initializes it as new ArrayList<>()), this
        // test
        // verifies the related edge case behavior.
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> original = new ClosePriceIndicator(series);

        // Create a descriptor manually with no components (empty list)
        ComponentDescriptor descriptor = ComponentDescriptor.builder().withType("ClosePriceIndicator").build();

        // Verify the descriptor has no components
        assertThat(descriptor.getComponents()).isEmpty();

        // Deserialize through the public API - this exercises the code path where
        // components is an empty list (not null, but exercises similar defensive logic)
        Indicator<?> reconstructed = IndicatorSerialization.fromDescriptor(series, descriptor);

        // Verify the indicator was reconstructed correctly
        assertThat(reconstructed).isInstanceOf(ClosePriceIndicator.class);
        assertThat(reconstructed.toDescriptor()).isEqualTo(descriptor);

        // Verify it produces the same values
        for (int i = series.getBeginIndex(); i <= series.getEndIndex(); i++) {
            assertThat(reconstructed.getValue(i)).isEqualTo(original.getValue(i));
        }

        // Also test via JSON round-trip
        String json = original.toJson();
        Indicator<?> fromJson = IndicatorSerialization.fromJson(series, json);
        assertThat(fromJson).isInstanceOf(ClosePriceIndicator.class);
        assertThat(fromJson.toDescriptor()).isEqualTo(descriptor);
    }

    /**
     * Test indicator class that allows circular references for testing purposes.
     * This class has a field that can reference another indicator, enabling
     * circular reference scenarios.
     */
    private static class CircularTestIndicator extends CachedIndicator<Num> {
        @SuppressWarnings("unused")
        private final Indicator<Num> base;
        @SuppressWarnings("unused")
        private final String name;
        @SuppressWarnings("unused")
        private final int value;
        private Indicator<Num> referencedIndicator;

        public CircularTestIndicator(BarSeries series, Indicator<Num> base, String name, int value) {
            super(series);
            this.base = base;
            this.name = name;
            this.value = value;
        }

        public void setReferencedIndicator(Indicator<Num> referencedIndicator) {
            this.referencedIndicator = referencedIndicator;
        }

        @Override
        public int getCountOfUnstableBars() {
            return 0;
        }

        @Override
        protected Num calculate(int index) {
            Num baseValue = base.getValue(index);
            if (referencedIndicator != null) {
                Num refValue = referencedIndicator.getValue(index);
                Num two = getBarSeries().numFactory().numOf(2);
                return baseValue.plus(refValue).dividedBy(two);
            }
            return baseValue;
        }
    }

}
