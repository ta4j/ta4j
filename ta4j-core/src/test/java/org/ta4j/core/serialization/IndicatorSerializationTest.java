/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.serialization;

import java.util.List;

import org.junit.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.indicators.CachedIndicator;
import org.ta4j.core.indicators.IndicatorConstructorSelectionTestIndicator;
import org.ta4j.core.indicators.KalmanFilterIndicator;
import org.ta4j.core.indicators.ParabolicSarIndicator;
import org.ta4j.core.indicators.averages.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferencePercentageIndicator;
import org.ta4j.core.indicators.helpers.FixedIndicator;
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

        // Verify that only constructor parameters are serialized
        assertThat(descriptor.getParameters()).containsEntry("processNoise", "0.0001");
        assertThat(descriptor.getParameters()).containsEntry("measurementNoise", "0.001");

        // Verify that transient stateful fields are NOT serialized
        assertThat(descriptor.getParameters()).doesNotContainKey("lastProcessedIndex");
        assertThat(descriptor.getParameters()).doesNotContainKey("filter");

        // Verify only the expected parameters are present
        assertThat(descriptor.getParameters()).hasSize(2);

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
    public void serializeCircularIndicatorReference() {
        // Test that circular indicator references are handled gracefully
        // This test verifies the fix for infinite recursion on circular graphs
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);

        // Create two indicators that reference each other (circular reference)
        CircularTestIndicator indicatorA = new CircularTestIndicator(series, base, "A", 5);
        CircularTestIndicator indicatorB = new CircularTestIndicator(series, base, "B", 10);
        indicatorA.setReferencedIndicator(indicatorB);
        indicatorB.setReferencedIndicator(indicatorA);

        // Serialization should complete without StackOverflowError
        // The placeholder mechanism should detect the circular reference
        ComponentDescriptor descriptorA = indicatorA.toDescriptor();

        // Verify the descriptor structure
        assertThat(descriptorA.getType()).isEqualTo("CircularTestIndicator");
        // Only numeric parameters are serialized, so only 'value' will be present
        assertThat(descriptorA.getParameters()).containsEntry("value", 5);
        assertThat(descriptorA.getComponents()).hasSize(2); // base + indicatorB

        // Verify that indicatorB is referenced (circular reference detected)
        assertThat(descriptorA.getComponents()).anySatisfy(child -> {
            assertThat(child.getType()).isEqualTo("CircularTestIndicator");
            assertThat(child.getParameters()).containsEntry("value", 10);
        });

        // Verify JSON serialization also works
        String json = indicatorA.toJson();
        assertThat(json).contains("CircularTestIndicator");
        assertThat(json).contains("\"value\":5");
        assertThat(json).contains("\"value\":10");

        // Verify that the circular reference doesn't cause infinite recursion
        // by checking that the JSON doesn't contain excessive nesting
        long indicatorCount = json.chars().filter(ch -> ch == '{').count();
        // Should have reasonable nesting (base indicator + 2 circular indicators)
        assertThat(indicatorCount).isLessThan(20);
    }

    @Test
    public void serializeSelfReferencingIndicator() {
        // Test an indicator that references itself
        // Note: The serialization code explicitly excludes self-references (child !=
        // indicator),
        // so the self-reference field won't be serialized, but this test verifies that
        // having a self-reference doesn't cause infinite recursion or crashes
        BarSeries series = new MockBarSeriesBuilder().withData(1, 2, 3, 4, 5).build();
        Indicator<Num> base = new ClosePriceIndicator(series);

        CircularTestIndicator indicator = new CircularTestIndicator(series, base, "SelfRef", 7);
        indicator.setReferencedIndicator(indicator); // Self-reference

        // Serialization should complete without StackOverflowError
        // The self-reference is excluded by design, so only the base indicator will be
        // serialized
        ComponentDescriptor descriptor = indicator.toDescriptor();

        assertThat(descriptor.getType()).isEqualTo("CircularTestIndicator");
        // Only numeric parameters are serialized, so only 'value' will be present
        assertThat(descriptor.getParameters()).containsEntry("value", 7);
        // Self-references are excluded, so only the base indicator is included
        assertThat(descriptor.getComponents()).hasSize(1); // base only

        // Verify JSON serialization works
        String json = indicator.toJson();
        assertThat(json).contains("CircularTestIndicator");
        assertThat(json).contains("\"value\":7");

        // Verify no infinite recursion
        long indicatorCount = json.chars().filter(ch -> ch == '{').count();
        assertThat(indicatorCount).isLessThan(20);
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
