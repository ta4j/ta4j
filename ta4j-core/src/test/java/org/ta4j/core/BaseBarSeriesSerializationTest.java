/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.ObjectStreamClass;
import java.io.Serial;
import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.mocks.MockBarBuilderFactory;
import org.ta4j.core.num.DoubleNumFactory;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;

/**
 * Structural deserialization invariants are independent of numeric precision.
 */
class BaseBarSeriesSerializationTest {

    private final NumFactory numFactory = DoubleNumFactory.getInstance();
    private final BarBuilderFactory barBuilderFactory = new MockBarBuilderFactory();
    private List<Bar> testBars;

    @BeforeEach
    void setUp() {
        testBars = new ArrayList<>();
        Instant baseTime = Instant.parse("2024-01-01T00:00:00Z");
        for (int i = 0; i < 3; i++) {
            testBars.add(new TimeBarBuilder(numFactory).timePeriod(Duration.ofDays(1))
                    .endTime(baseTime.plus(Duration.ofDays(i)))
                    .openPrice(numFactory.numOf(i + 1))
                    .highPrice(numFactory.numOf(i + 2))
                    .lowPrice(numFactory.numOf(i))
                    .closePrice(numFactory.numOf(i + 1.5))
                    .volume(numFactory.numOf(i * 100))
                    .amount(numFactory.numOf(i * 1000))
                    .trades(i * 10)
                    .build());
        }
    }

    @Test
    public void deserializationRejectsMissingState() throws Exception {
        SerializedSeriesState missingBars = new SerializedSeriesState(numFactory, barBuilderFactory);
        missingBars.bars = null;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(missingBars));
        SerializedSeriesState missingNumFactory = new SerializedSeriesState(numFactory, barBuilderFactory);
        missingNumFactory.numFactory = null;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(missingNumFactory));
        SerializedSeriesState missingBarBuilder = new SerializedSeriesState(numFactory, barBuilderFactory);
        missingBarBuilder.barBuilderFactory = null;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(missingBarBuilder));
    }

    @Test
    public void deserializationRejectsInvalidMaximumBarCount() throws Exception {
        for (int maximum : new int[] { 0, -1 }) {
            SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
            state.maximumBarCount = maximum;
            assertThrows(InvalidObjectException.class, () -> restoreSeriesState(state));
        }
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.bars = new ArrayList<>(testBars.subList(0, 2));
        state.seriesBeginIndex = 0;
        state.seriesEndIndex = 1;
        state.maximumBarCount = 1;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(state));
    }

    @Test
    public void deserializationRejectsNullBars() throws Exception {
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.bars = Arrays.asList(testBars.get(0), null);
        state.seriesBeginIndex = 0;
        state.seriesEndIndex = 1;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(state));
    }

    @Test
    public void deserializationRejectsOverflowingRetainedIndexes() throws Exception {
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.bars = new ArrayList<>(testBars.subList(0, 2));
        state.removedBarsCount = Integer.MAX_VALUE;
        state.seriesBeginIndex = Integer.MAX_VALUE;
        state.seriesEndIndex = Integer.MAX_VALUE;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(state));
    }

    @Test
    public void deserializedExhaustedRetainedOffsetRejectsAppendWithoutMutation() throws Exception {
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.bars = new ArrayList<>(List.of(testBars.get(0)));
        state.removedBarsCount = Integer.MAX_VALUE;
        state.seriesBeginIndex = Integer.MAX_VALUE;
        state.seriesEndIndex = Integer.MAX_VALUE - 1;
        BaseBarSeries restored = restoreSeriesState(state);
        Bar retainedBar = restored.getBar(Integer.MAX_VALUE);
        long revision = restored.getBarHistoryRevision();

        assertThrows(ArithmeticException.class, () -> restored.addBar(testBars.get(1)));

        assertEquals(List.of(retainedBar), restored.getBarData());
        assertSame(retainedBar, restored.getBar(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, restored.getBeginIndex());
        assertEquals(Integer.MAX_VALUE - 1, restored.getEndIndex());
        assertEquals(Integer.MAX_VALUE, restored.getRemovedBarsCount());
        assertEquals(revision, restored.getBarHistoryRevision());
    }

    @Test
    public void deserializedTruncatedRangesAppendAtRetainedTail() throws Exception {
        for (int offset : new int[] { 0, 7 }) {
            for (int logicalLength : new int[] { 0, 1 }) {
                SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
                state.bars = new ArrayList<>(testBars.subList(0, 2));
                state.removedBarsCount = offset;
                state.seriesBeginIndex = offset;
                state.seriesEndIndex = offset + logicalLength - 1;
                BaseBarSeries restored = restoreSeriesState(state);
                Bar appended = testBars.get(2);

                restored.addBar(appended);

                assertEquals(offset + 2, restored.getEndIndex());
                assertSame(appended, restored.getLastBar());
                assertSame(appended, restored.getBar(offset + 2));
                assertEquals(3, restored.getBarCount());
                assertEquals(offset, restored.getBeginIndex());
                assertEquals(offset, restored.getRemovedBarsCount());
            }
        }
    }

    @Test
    public void deserializedTruncatedRangeReplacementReportsRetainedTailIndex() throws Exception {
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.bars = new ArrayList<>(testBars.subList(0, 2));
        state.removedBarsCount = 7;
        state.seriesBeginIndex = 7;
        state.seriesEndIndex = 7;
        BaseBarSeries restored = restoreSeriesState(state);
        long revision = restored.getBarHistoryRevision();
        Bar replacement = testBars.get(2);

        restored.addBar(replacement, true);

        assertSame(replacement, restored.getBar(8));
        assertEquals(7, restored.getEndIndex());
        assertEquals(8, restored.getBarSeriesChangeSnapshot(revision).earliestChangedIndex());
    }

    @Test
    public void deserializationRejectsInvalidHistoryRevisions() throws Exception {
        for (long revision : new long[] { -1L, Long.MIN_VALUE, Long.MAX_VALUE }) {
            SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
            state.barHistoryRevision = revision;
            assertThrows(InvalidObjectException.class, () -> restoreSeriesState(state));
        }
    }

    @Test
    public void deserializationRejectsBoundedConstrainedSeries() throws Exception {
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.constrained = true;
        state.maximumBarCount = 2;
        assertThrows(InvalidObjectException.class, () -> restoreSeriesState(state));
    }

    @Test
    public void deserializedImmutableListsSupportRetentionMutationAndClear() throws Exception {
        for (List<Bar> bars : List.of(Arrays.asList(testBars.get(0), testBars.get(1)),
                List.of(testBars.get(0), testBars.get(1)))) {
            SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
            state.bars = bars;
            state.maximumBarCount = 2;
            state.seriesBeginIndex = 0;
            state.seriesEndIndex = 1;
            state.barHistoryRevision = 17;
            BaseBarSeries restored = restoreSeriesState(state);
            restored.addPrice(numFactory.numOf(42));
            assertEquals(18, restored.getBarHistoryRevision());
            assertNumEquals(42, restored.getLastBar().getClosePrice());
            appendBar(restored);
            assertEquals(1, restored.getBeginIndex());
            assertEquals(2, restored.getEndIndex());
            assertNumEquals(42, restored.getBar(1).getClosePrice());
            restored.clear();
            assertTrue(restored.isEmpty());
            assertEquals(-1, restored.getBeginIndex());
            assertEquals(-1, restored.getEndIndex());
            restored.addBar(testBars.get(0));
            assertEquals(0, restored.getBeginIndex());
            assertEquals(0, restored.getEndIndex());
            assertEquals(testBars.get(0).getClosePrice(), restored.getBar(0).getClosePrice());
        }
    }

    @Test
    public void deserializedBoundedSeriesRetainsOffsetsAndAcceptsNewBars() throws Exception {
        SerializedSeriesState state = new SerializedSeriesState(numFactory, barBuilderFactory);
        state.bars = new ArrayList<>(testBars.subList(0, 2));
        state.maximumBarCount = 2;
        state.removedBarsCount = 7;
        state.seriesBeginIndex = 7;
        state.seriesEndIndex = 8;
        BaseBarSeries restored = restoreSeriesState(state);
        assertEquals(7, restored.getBeginIndex());
        assertEquals(8, restored.getEndIndex());
        assertEquals(testBars.get(1).getClosePrice(), restored.getBar(8).getClosePrice());
        appendBar(restored);
        assertEquals(8, restored.getBeginIndex());
        assertEquals(9, restored.getEndIndex());
        assertEquals(2, restored.getBarCount());
        assertEquals(testBars.get(1).getEndTime().plus(testBars.get(1).getTimePeriod()),
                restored.getBar(9).getEndTime());
    }

    private BaseBarSeries restoreSeriesState(SerializedSeriesState state) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(state);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override
            protected ObjectStreamClass readClassDescriptor() throws IOException, ClassNotFoundException {
                ObjectStreamClass descriptor = super.readClassDescriptor();
                return descriptor.getName().equals(SerializedSeriesState.class.getName())
                        ? ObjectStreamClass.lookup(BaseBarSeries.class)
                        : descriptor;
            }
        }) {
            return (BaseBarSeries) input.readObject();
        }
    }

    /**
     * Writes legacy series state without mutating private production fields. Its
     * fields match the serialized BaseBarSeries schema, not its runtime helpers.
     */
    private static final class SerializedSeriesState implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;
        private long barHistoryRevision;
        private BarBuilderFactory barBuilderFactory;
        private List<Bar> bars = new ArrayList<>();
        private boolean constrained;
        private int maximumBarCount = Integer.MAX_VALUE;
        private String name = "serialized-series";
        private NumFactory numFactory;
        private int removedBarsCount;
        private int seriesBeginIndex = -1;
        private int seriesEndIndex = -1;

        private SerializedSeriesState(NumFactory numFactory, BarBuilderFactory barBuilderFactory) {
            this.numFactory = numFactory;
            this.barBuilderFactory = barBuilderFactory;
        }
    }

    private void appendBar(final BaseBarSeries series) {
        final Bar lastBar = series.getLastBar();
        final Num closePrice = lastBar.getClosePrice();
        final Instant endTime = lastBar.getEndTime().plus(lastBar.getTimePeriod());
        series.addBar(new BaseBar(lastBar.getTimePeriod(), lastBar.getEndTime(), endTime, closePrice, closePrice,
                closePrice, closePrice, numFactory.zero(), numFactory.zero(), 0));
    }

}
