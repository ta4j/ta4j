/*
 * SPDX-License-Identifier: MIT
 */
package ta4jexamples.charting.annotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.BarSeries;
import org.ta4j.core.mocks.MockBarSeriesBuilder;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.BarLabel;
import ta4jexamples.charting.annotation.BarSeriesLabelIndicator.LabelPlacement;

/**
 * Comprehensive unit tests for {@link BarSeriesLabelIndicator}.
 */
class BarSeriesLabelIndicatorTest {

    private BarSeries series;
    private NumFactory numFactory;

    @BeforeEach
    void setUp() {
        numFactory = org.ta4j.core.num.DecimalNumFactory.getInstance();
        series = new MockBarSeriesBuilder().withNumFactory(numFactory)
                .withData(100.0, 101.0, 102.0, 103.0, 104.0, 105.0, 106.0, 107.0, 108.0, 109.0)
                .build();
    }

    // ========== Constructor Tests ==========

    @Test
    void testConstructorWithValidInputs() {
        List<BarLabel> labels = List.of(new BarLabel(0, numFactory.numOf(100.0), "Label 0", LabelPlacement.CENTER),
                new BarLabel(5, numFactory.numOf(105.0), "Label 5", LabelPlacement.ABOVE));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        assertNotNull(indicator);
        assertSame(series, indicator.getBarSeries());
        assertEquals(2, indicator.labels().size());
    }

    @Test
    void testConstructorThrowsWhenSeriesIsNull() {
        List<BarLabel> labels = List.of(new BarLabel(0, numFactory.numOf(100.0), "Label", LabelPlacement.CENTER));

        assertThrows(NullPointerException.class, () -> {
            new BarSeriesLabelIndicator(null, labels);
        });
    }

    @Test
    void testConstructorThrowsWhenLabelsIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new BarSeriesLabelIndicator(series, null);
        });
    }

    @Test
    void testConstructorFiltersNullLabels() {
        List<BarLabel> labels = new ArrayList<>();
        labels.add(new BarLabel(0, numFactory.numOf(100.0), "Label 0", LabelPlacement.CENTER));
        labels.add(null);
        labels.add(new BarLabel(5, numFactory.numOf(105.0), "Label 5", LabelPlacement.ABOVE));
        labels.add(null);

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        assertEquals(2, indicator.labels().size());
        assertEquals(0, indicator.labels().get(0).barIndex());
        assertEquals(5, indicator.labels().get(1).barIndex());
    }

    @Test
    void testConstructorWithEmptyLabels() {
        List<BarLabel> emptyLabels = Collections.emptyList();

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, emptyLabels);

        assertNotNull(indicator);
        assertTrue(indicator.labels().isEmpty());
    }

    @Test
    void testConstructorSortsLabelsByBarIndex() {
        List<BarLabel> labels = new ArrayList<>();
        labels.add(new BarLabel(5, numFactory.numOf(105.0), "Label 5", LabelPlacement.ABOVE));
        labels.add(new BarLabel(2, numFactory.numOf(102.0), "Label 2", LabelPlacement.BELOW));
        labels.add(new BarLabel(0, numFactory.numOf(100.0), "Label 0", LabelPlacement.CENTER));
        labels.add(new BarLabel(8, numFactory.numOf(108.0), "Label 8", LabelPlacement.ABOVE));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> sortedLabels = indicator.labels();
        assertEquals(4, sortedLabels.size());
        assertEquals(0, sortedLabels.get(0).barIndex());
        assertEquals(2, sortedLabels.get(1).barIndex());
        assertEquals(5, sortedLabels.get(2).barIndex());
        assertEquals(8, sortedLabels.get(3).barIndex());
    }

    @Test
    void testConstructorHandlesDuplicateBarIndices() {
        List<BarLabel> labels = new ArrayList<>();
        labels.add(new BarLabel(3, numFactory.numOf(103.0), "First", LabelPlacement.CENTER));
        labels.add(new BarLabel(5, numFactory.numOf(105.0), "Middle", LabelPlacement.ABOVE));
        labels.add(new BarLabel(3, numFactory.numOf(303.0), "Second", LabelPlacement.BELOW)); // Duplicate index

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> resultLabels = indicator.labels();
        // The labels list keeps all labels (including duplicates), but sorted
        assertEquals(3, resultLabels.size());
        assertEquals(3, resultLabels.get(0).barIndex());
        assertEquals(3, resultLabels.get(1).barIndex());
        assertEquals(5, resultLabels.get(2).barIndex());

        // getValue() returns the last value for duplicate indices
        assertEquals(303.0, indicator.getValue(3).doubleValue(), 0.001);
    }

    // ========== BarLabel Record Tests ==========

    @Test
    void testBarLabelCreationWithValidInputs() {
        BarLabel label = new BarLabel(5, numFactory.numOf(105.0), "Test", LabelPlacement.ABOVE);

        assertEquals(5, label.barIndex());
        assertEquals(105.0, label.yValue().doubleValue(), 0.001);
        assertEquals("Test", label.text());
        assertEquals(LabelPlacement.ABOVE, label.placement());
    }

    @Test
    void testBarLabelThrowsWhenBarIndexIsNegative() {
        assertThrows(IllegalArgumentException.class, () -> {
            new BarLabel(-1, numFactory.numOf(100.0), "Test", LabelPlacement.CENTER);
        });
    }

    @Test
    void testBarLabelThrowsWhenYValueIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new BarLabel(0, null, "Test", LabelPlacement.CENTER);
        });
    }

    @Test
    void testBarLabelThrowsWhenTextIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new BarLabel(0, numFactory.numOf(100.0), null, LabelPlacement.CENTER);
        });
    }

    @Test
    void testBarLabelThrowsWhenPlacementIsNull() {
        assertThrows(NullPointerException.class, () -> {
            new BarLabel(0, numFactory.numOf(100.0), "Test", null);
        });
    }

    @Test
    void testBarLabelAcceptsZeroBarIndex() {
        BarLabel label = new BarLabel(0, numFactory.numOf(100.0), "Test", LabelPlacement.CENTER);
        assertEquals(0, label.barIndex());
    }

    @Test
    void testBarLabelAcceptsEmptyText() {
        BarLabel label = new BarLabel(0, numFactory.numOf(100.0), "", LabelPlacement.CENTER);
        assertEquals("", label.text());
    }

    @Test
    void testBarLabelAllPlacementValues() {
        BarLabel above = new BarLabel(0, numFactory.numOf(100.0), "Above", LabelPlacement.ABOVE);
        BarLabel below = new BarLabel(1, numFactory.numOf(101.0), "Below", LabelPlacement.BELOW);
        BarLabel center = new BarLabel(2, numFactory.numOf(102.0), "Center", LabelPlacement.CENTER);

        assertEquals(LabelPlacement.ABOVE, above.placement());
        assertEquals(LabelPlacement.BELOW, below.placement());
        assertEquals(LabelPlacement.CENTER, center.placement());
    }

    // ========== getValue() Tests ==========

    @Test
    void testGetValueReturnsYValueForLabeledIndex() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER),
                new BarLabel(7, numFactory.numOf(200.0), "Label 7", LabelPlacement.ABOVE));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value3 = indicator.getValue(3);
        assertEquals(150.0, value3.doubleValue(), 0.001);

        Num value7 = indicator.getValue(7);
        assertEquals(200.0, value7.doubleValue(), 0.001);
    }

    @Test
    void testGetValueReturnsNaNForUnlabeledIndex() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value0 = indicator.getValue(0);
        assertTrue(value0.isNaN());

        Num value5 = indicator.getValue(5);
        assertTrue(value5.isNaN());

        Num value9 = indicator.getValue(9);
        assertTrue(value9.isNaN());
    }

    @Test
    void testGetValueReturnsNaNForIndexBeforeSeries() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        // Even if we query an index that doesn't exist in the series,
        // we should get NaN if it's not labeled
        Num value = indicator.getValue(-1);
        assertTrue(value.isNaN());
    }

    @Test
    void testGetValueReturnsNaNForIndexAfterSeries() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value = indicator.getValue(100);
        assertTrue(value.isNaN());
    }

    @Test
    void testGetValueWithEmptyLabels() {
        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, Collections.emptyList());

        for (int i = 0; i <= series.getEndIndex(); i++) {
            Num value = indicator.getValue(i);
            assertTrue(value.isNaN());
        }
    }

    @Test
    void testGetValueWithSingleLabel() {
        List<BarLabel> labels = List.of(new BarLabel(5, numFactory.numOf(250.0), "Single", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value5 = indicator.getValue(5);
        assertEquals(250.0, value5.doubleValue(), 0.001);

        Num value0 = indicator.getValue(0);
        assertTrue(value0.isNaN());

        Num value9 = indicator.getValue(9);
        assertTrue(value9.isNaN());
    }

    @Test
    void testGetValueWithLabelsAtBoundaries() {
        int beginIndex = series.getBeginIndex();
        int endIndex = series.getEndIndex();

        List<BarLabel> labels = List.of(
                new BarLabel(beginIndex, numFactory.numOf(100.0), "Begin", LabelPlacement.CENTER),
                new BarLabel(endIndex, numFactory.numOf(109.0), "End", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num beginValue = indicator.getValue(beginIndex);
        assertEquals(100.0, beginValue.doubleValue(), 0.001);

        Num endValue = indicator.getValue(endIndex);
        assertEquals(109.0, endValue.doubleValue(), 0.001);
    }

    @Test
    void testGetValueCachingBehavior() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value1 = indicator.getValue(3);
        Num value2 = indicator.getValue(3);
        Num value3 = indicator.getValue(3);

        // Values should be equal (cached)
        assertEquals(value1, value2);
        assertEquals(value2, value3);
        // Since it's a CachedIndicator, same instance should be returned
        assertSame(value1, value2);
        assertSame(value2, value3);
    }

    // ========== labels() Tests ==========

    @Test
    void testLabelsReturnsOrderedList() {
        List<BarLabel> labels = new ArrayList<>();
        labels.add(new BarLabel(8, numFactory.numOf(108.0), "Label 8", LabelPlacement.ABOVE));
        labels.add(new BarLabel(1, numFactory.numOf(101.0), "Label 1", LabelPlacement.BELOW));
        labels.add(new BarLabel(5, numFactory.numOf(105.0), "Label 5", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();
        assertEquals(3, result.size());
        assertEquals(1, result.get(0).barIndex());
        assertEquals(5, result.get(1).barIndex());
        assertEquals(8, result.get(2).barIndex());
    }

    @Test
    void testLabelsReturnsImmutableList() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();

        assertThrows(UnsupportedOperationException.class, () -> {
            result.add(new BarLabel(5, numFactory.numOf(105.0), "New", LabelPlacement.ABOVE));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            result.remove(0);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            result.clear();
        });
    }

    @Test
    void testLabelsProtectsInternalRepresentation() {
        // Create indicator with multiple labels
        List<BarLabel> originalLabels = new ArrayList<>();
        originalLabels.add(new BarLabel(2, numFactory.numOf(102.0), "Label 2", LabelPlacement.CENTER));
        originalLabels.add(new BarLabel(5, numFactory.numOf(105.0), "Label 5", LabelPlacement.ABOVE));
        originalLabels.add(new BarLabel(8, numFactory.numOf(108.0), "Label 8", LabelPlacement.BELOW));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, originalLabels);

        // Get the list and verify initial state
        List<BarLabel> returnedList1 = indicator.labels();
        assertEquals(3, returnedList1.size());
        assertEquals(2, returnedList1.get(0).barIndex());
        assertEquals(5, returnedList1.get(1).barIndex());
        assertEquals(8, returnedList1.get(2).barIndex());

        // Verify getValue() works correctly
        assertEquals(102.0, indicator.getValue(2).doubleValue(), 0.001);
        assertEquals(105.0, indicator.getValue(5).doubleValue(), 0.001);
        assertEquals(108.0, indicator.getValue(8).doubleValue(), 0.001);

        // Attempt to modify the returned list - all should fail
        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.add(new BarLabel(10, numFactory.numOf(110.0), "New Label", LabelPlacement.CENTER));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.add(0, new BarLabel(1, numFactory.numOf(101.0), "Inserted", LabelPlacement.CENTER));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.remove(0);
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.remove(returnedList1.get(0));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.set(0, new BarLabel(99, numFactory.numOf(999.0), "Replaced", LabelPlacement.CENTER));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.clear();
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.addAll(List.of(new BarLabel(10, numFactory.numOf(110.0), "New", LabelPlacement.CENTER)));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.removeAll(List.of(returnedList1.get(0)));
        });

        assertThrows(UnsupportedOperationException.class, () -> {
            returnedList1.retainAll(Collections.emptyList());
        });

        // Verify internal state is unchanged - get the list again
        List<BarLabel> returnedList2 = indicator.labels();
        assertEquals(3, returnedList2.size(), "Internal list size should remain unchanged");
        assertEquals(2, returnedList2.get(0).barIndex(), "First label should be unchanged");
        assertEquals(5, returnedList2.get(1).barIndex(), "Second label should be unchanged");
        assertEquals(8, returnedList2.get(2).barIndex(), "Third label should be unchanged");

        // Verify getValue() still works correctly - internal state is protected
        assertEquals(102.0, indicator.getValue(2).doubleValue(), 0.001, "getValue(2) should still work");
        assertEquals(105.0, indicator.getValue(5).doubleValue(), 0.001, "getValue(5) should still work");
        assertEquals(108.0, indicator.getValue(8).doubleValue(), 0.001, "getValue(8) should still work");

        // Verify unlabeled indices still return NaN
        assertTrue(indicator.getValue(0).isNaN(), "Unlabeled index should return NaN");
        assertTrue(indicator.getValue(9).isNaN(), "Unlabeled index should return NaN");
    }

    @Test
    void testLabelsReturnsCachedInstance() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result1 = indicator.labels();
        List<BarLabel> result2 = indicator.labels();

        // Should return the same list instance (cached unmodifiable list)
        assertSame(result1, result2, "Each call should return the same cached instance");
        assertEquals(result1, result2, "Both should contain the same content");
        assertEquals(result1.size(), result2.size(), "Both should have the same size");
    }

    @Test
    void testLabelsWithEmptyList() {
        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, Collections.emptyList());

        List<BarLabel> result = indicator.labels();
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void testLabelsPreservesAllLabelProperties() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Test Label", LabelPlacement.ABOVE),
                new BarLabel(7, numFactory.numOf(200.0), "Another Label", LabelPlacement.BELOW));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();
        assertEquals(2, result.size());

        BarLabel label0 = result.get(0);
        assertEquals(3, label0.barIndex());
        assertEquals(150.0, label0.yValue().doubleValue(), 0.001);
        assertEquals("Test Label", label0.text());
        assertEquals(LabelPlacement.ABOVE, label0.placement());

        BarLabel label1 = result.get(1);
        assertEquals(7, label1.barIndex());
        assertEquals(200.0, label1.yValue().doubleValue(), 0.001);
        assertEquals("Another Label", label1.text());
        assertEquals(LabelPlacement.BELOW, label1.placement());
    }

    // ========== getCountOfUnstableBars() Tests ==========

    @Test
    void testGetCountOfUnstableBarsReturnsZero() {
        List<BarLabel> labels = List.of(new BarLabel(3, numFactory.numOf(150.0), "Label 3", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        assertEquals(0, indicator.getCountOfUnstableBars());
    }

    // ========== Integration Tests ==========

    @Test
    void testIndicatorWithManyLabels() {
        List<BarLabel> labels = new ArrayList<>();
        for (int i = 0; i <= series.getEndIndex(); i++) {
            labels.add(new BarLabel(i, numFactory.numOf(100.0 + i), "Label " + i, LabelPlacement.CENTER));
        }

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        assertEquals(series.getBarCount(), indicator.labels().size());

        for (int i = 0; i <= series.getEndIndex(); i++) {
            Num value = indicator.getValue(i);
            assertEquals(100.0 + i, value.doubleValue(), 0.001);
        }
    }

    @Test
    void testIndicatorWithSparseLabels() {
        List<BarLabel> labels = List.of(new BarLabel(0, numFactory.numOf(100.0), "Start", LabelPlacement.ABOVE),
                new BarLabel(5, numFactory.numOf(105.0), "Middle", LabelPlacement.CENTER),
                new BarLabel(9, numFactory.numOf(109.0), "End", LabelPlacement.BELOW));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        // Check labeled indices
        assertEquals(100.0, indicator.getValue(0).doubleValue(), 0.001);
        assertEquals(105.0, indicator.getValue(5).doubleValue(), 0.001);
        assertEquals(109.0, indicator.getValue(9).doubleValue(), 0.001);

        // Check unlabeled indices
        assertTrue(indicator.getValue(1).isNaN());
        assertTrue(indicator.getValue(2).isNaN());
        assertTrue(indicator.getValue(3).isNaN());
        assertTrue(indicator.getValue(4).isNaN());
        assertTrue(indicator.getValue(6).isNaN());
        assertTrue(indicator.getValue(7).isNaN());
        assertTrue(indicator.getValue(8).isNaN());
    }

    @Test
    void testIndicatorWithAllPlacementTypes() {
        List<BarLabel> labels = List.of(new BarLabel(0, numFactory.numOf(100.0), "Above", LabelPlacement.ABOVE),
                new BarLabel(1, numFactory.numOf(101.0), "Below", LabelPlacement.BELOW),
                new BarLabel(2, numFactory.numOf(102.0), "Center", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();
        assertEquals(3, result.size());
        assertEquals(LabelPlacement.ABOVE, result.get(0).placement());
        assertEquals(LabelPlacement.BELOW, result.get(1).placement());
        assertEquals(LabelPlacement.CENTER, result.get(2).placement());
    }

    @Test
    void testIndicatorWithDuplicateIndicesKeepsLast() {
        List<BarLabel> labels = new ArrayList<>();
        labels.add(new BarLabel(5, numFactory.numOf(105.0), "First", LabelPlacement.ABOVE));
        labels.add(new BarLabel(3, numFactory.numOf(103.0), "Second", LabelPlacement.BELOW));
        labels.add(new BarLabel(5, numFactory.numOf(205.0), "Third", LabelPlacement.CENTER)); // Duplicate
        labels.add(new BarLabel(7, numFactory.numOf(107.0), "Fourth", LabelPlacement.ABOVE));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();
        // The labels list keeps all labels (including duplicates), but sorted
        assertEquals(4, result.size());
        assertEquals(3, result.get(0).barIndex());
        assertEquals(5, result.get(1).barIndex());
        assertEquals(5, result.get(2).barIndex());
        assertEquals(7, result.get(3).barIndex());

        // Check that getValue returns the last value for duplicate indices
        assertEquals(205.0, indicator.getValue(5).doubleValue(), 0.001);

        // The labels list contains both labels for index 5, but getValue uses the map
        // which keeps the last
        List<BarLabel> labelsAt5 = result.stream().filter(l -> l.barIndex() == 5).toList();
        assertEquals(2, labelsAt5.size());
        assertEquals("First", labelsAt5.get(0).text());
        assertEquals("Third", labelsAt5.get(1).text());
    }

    @Test
    void testIndicatorWithNullLabelsFiltered() {
        List<BarLabel> labels = new ArrayList<>();
        labels.add(new BarLabel(0, numFactory.numOf(100.0), "Valid 1", LabelPlacement.CENTER));
        labels.add(null);
        labels.add(new BarLabel(5, numFactory.numOf(105.0), "Valid 2", LabelPlacement.ABOVE));
        labels.add(null);
        labels.add(null);
        labels.add(new BarLabel(9, numFactory.numOf(109.0), "Valid 3", LabelPlacement.BELOW));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();
        assertEquals(3, result.size());
        assertEquals(0, result.get(0).barIndex());
        assertEquals(5, result.get(1).barIndex());
        assertEquals(9, result.get(2).barIndex());
    }

    @Test
    void testIndicatorWithUnsortedLabels() {
        // Create labels in reverse order
        List<BarLabel> labels = new ArrayList<>();
        for (int i = series.getEndIndex(); i >= 0; i--) {
            labels.add(new BarLabel(i, numFactory.numOf(100.0 + i), "Label " + i, LabelPlacement.CENTER));
        }

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        List<BarLabel> result = indicator.labels();
        assertEquals(series.getBarCount(), result.size());

        // Verify they are sorted
        for (int i = 0; i < result.size(); i++) {
            assertEquals(i, result.get(i).barIndex());
        }
    }

    @Test
    void testIndicatorWithLargeBarIndex() {
        // Test with a bar index that's larger than the series size
        List<BarLabel> labels = List
                .of(new BarLabel(100, numFactory.numOf(1000.0), "Large Index", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        // Should still work - the indicator doesn't validate bar index bounds
        assertEquals(1, indicator.labels().size());
        assertEquals(1000.0, indicator.getValue(100).doubleValue(), 0.001);
        assertTrue(indicator.getValue(0).isNaN());
    }

    @Test
    void testIndicatorWithZeroValue() {
        List<BarLabel> labels = List.of(new BarLabel(5, numFactory.numOf(0.0), "Zero", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value = indicator.getValue(5);
        assertEquals(0.0, value.doubleValue(), 0.001);
        assertFalse(value.isNaN());
    }

    @Test
    void testIndicatorWithNegativeValue() {
        List<BarLabel> labels = List.of(new BarLabel(5, numFactory.numOf(-50.0), "Negative", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value = indicator.getValue(5);
        assertEquals(-50.0, value.doubleValue(), 0.001);
        assertFalse(value.isNaN());
    }

    @Test
    void testIndicatorWithVeryLargeValue() {
        List<BarLabel> labels = List.of(new BarLabel(5, numFactory.numOf(1e10), "Large", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value = indicator.getValue(5);
        assertEquals(1e10, value.doubleValue(), 0.001);
    }

    @Test
    void testIndicatorWithVerySmallValue() {
        List<BarLabel> labels = List.of(new BarLabel(5, numFactory.numOf(1e-10), "Small", LabelPlacement.CENTER));

        BarSeriesLabelIndicator indicator = new BarSeriesLabelIndicator(series, labels);

        Num value = indicator.getValue(5);
        assertEquals(1e-10, value.doubleValue(), 0.0001);
    }
}
