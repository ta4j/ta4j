/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import static org.junit.Assert.*;

import java.io.*;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.Before;
import org.junit.Test;
import org.ta4j.core.bars.TimeBarBuilder;
import org.ta4j.core.indicators.averages.EMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.num.DecimalNumFactory;
import org.ta4j.core.num.NumFactory;

public class BarSeriesListenerTest {

    private final NumFactory numFactory = DecimalNumFactory.getInstance();
    private BaseBarSeries series;
    private RecordingListener listener;
    private Instant baseTime;

    @Before
    public void setUp() {
        series = (BaseBarSeries) new BaseBarSeriesBuilder()
                .withNumFactory(numFactory)
                .withName("ListenerTest")
                .build();
        listener = new RecordingListener();
        baseTime = Instant.parse("2024-01-01T00:00:00Z");
    }

    // ─── addListener ───

    @Test
    public void addListener_null_isNoOp() {
        series.addListener(null);
        addBar(0);
    }

    @Test
    public void addListener_duplicate_isIgnored() {
        series.addListener(listener);
        series.addListener(listener);
        addBar(0);
        assertEquals("duplicate listener should not double-fire", 1, listener.added.size());
    }

    @Test
    public void addListener_multipleDifferentListeners() {
        RecordingListener listener2 = new RecordingListener();
        series.addListener(listener);
        series.addListener(listener2);
        addBar(0);
        assertEquals(1, listener.added.size());
        assertEquals(1, listener2.added.size());
    }

    // ─── removeListener ───

    @Test
    public void removeListener_stopsNotifications() {
        series.addListener(listener);
        series.removeListener(listener);
        addBar(0);
        assertTrue(listener.added.isEmpty());
    }

    @Test
    public void removeListener_unknownListener_isNoOp() {
        series.addListener(listener);
        series.removeListener(new RecordingListener());
        addBar(0);
        assertEquals(1, listener.added.size());
    }

    // ─── onBarAdded ───

    @Test
    public void onBarAdded_firesOnAppend() {
        series.addListener(listener);
        addBar(0);
        assertEquals(1, listener.added.size());
        assertEquals(Integer.valueOf(0), listener.added.get(0));
        assertTrue(listener.replaced.isEmpty());
    }

    @Test
    public void onBarAdded_correctIndexOnMultipleAppends() {
        series.addListener(listener);
        addBar(0);
        addBar(1);
        addBar(2);
        assertEquals(3, listener.added.size());
        assertEquals(Integer.valueOf(0), listener.added.get(0));
        assertEquals(Integer.valueOf(1), listener.added.get(1));
        assertEquals(Integer.valueOf(2), listener.added.get(2));
    }

    @Test
    public void onBarAdded_receivesCorrectBar() {
        series.addListener(listener);
        addBar(0);
        assertNotNull(listener.addedBars.get(0));
    }

    // ─── onBarReplaced ───

    @Test
    public void onBarReplaced_firesOnNonEmptyReplace() {
        addBar(0);
        series.addListener(listener);
        series.addBar(createBar(0), true);
        assertEquals(1, listener.replaced.size());
        assertEquals(Integer.valueOf(0), listener.replaced.get(0));
        assertTrue(listener.added.isEmpty());
    }

    @Test
    public void onBarReplaced_multipleReplacements() {
        addBar(0);
        series.addListener(listener);
        series.addBar(createBar(0), true);
        series.addBar(createBar(0), true);
        series.addBar(createBar(0), true);
        assertEquals(3, listener.replaced.size());
        assertTrue(listener.added.isEmpty());
    }

    @Test
    public void addBar_replaceOnEmpty_addsInsteadOfReplace() {
        series.addListener(listener);
        series.addBar(createBar(0), true);
        assertEquals(1, listener.added.size());
        assertTrue(listener.replaced.isEmpty());
    }

    @Test
    public void addBar_appendThenReplaceThenAppend() {
        series.addListener(listener);
        addBar(0);
        series.addBar(createBar(0), true);
        addBar(1);
        assertEquals(2, listener.added.size());
        assertEquals(1, listener.replaced.size());
    }

    // ─── max bar count ───

    @Test
    public void onBarAdded_firesWithMaxBarCount() {
        series.setMaximumBarCount(2);
        series.addListener(listener);
        addBar(0);
        addBar(1);
        addBar(2);
        assertEquals(3, listener.added.size());
    }

    @Test
    public void onBarReplaced_firesWithMaxBarCount() {
        series.setMaximumBarCount(2);
        addBar(0);
        addBar(1);
        series.addListener(listener);
        series.addBar(createBar(1), true);
        assertEquals(1, listener.replaced.size());
    }

    // ─── deserialization ───

    @Test
    public void listenerWorksAfterDeserialization() throws Exception {
        addBar(0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ObjectOutputStream(bos).writeObject(series);
        BaseBarSeries deserialized = (BaseBarSeries) new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray())).readObject();

        RecordingListener newListener = new RecordingListener();
        deserialized.addListener(newListener);

        Bar newBar = new TimeBarBuilder(numFactory)
                .timePeriod(Duration.ofDays(1))
                .endTime(baseTime.plus(Duration.ofDays(1)))
                .openPrice(numFactory.numOf(100))
                .highPrice(numFactory.numOf(105))
                .lowPrice(numFactory.numOf(95))
                .closePrice(numFactory.numOf(102))
                .volume(numFactory.numOf(1000))
                .build();
        deserialized.addBar(newBar, false);
        assertEquals(1, newListener.added.size());
    }

    @Test
    public void removeListener_worksAfterDeserialization() throws Exception {
        addBar(0);

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        new ObjectOutputStream(bos).writeObject(series);
        BaseBarSeries deserialized = (BaseBarSeries) new ObjectInputStream(
                new ByteArrayInputStream(bos.toByteArray())).readObject();

        RecordingListener newListener = new RecordingListener();
        deserialized.addListener(newListener);
        deserialized.removeListener(newListener);

        Bar newBar = new TimeBarBuilder(numFactory)
                .timePeriod(Duration.ofDays(1))
                .endTime(baseTime.plus(Duration.ofDays(1)))
                .openPrice(numFactory.numOf(100))
                .highPrice(numFactory.numOf(105))
                .lowPrice(numFactory.numOf(95))
                .closePrice(numFactory.numOf(102))
                .volume(numFactory.numOf(1000))
                .build();
        deserialized.addBar(newBar, false);
        assertTrue(newListener.added.isEmpty());
    }

    // ─── CachedIndicator subscription ───

    @Test
    public void cachedIndicator_computesOnNewBar() {
        for (int i = 0; i < 20; i++) {
            addBar(i);
        }

        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        EMAIndicator ema = new EMAIndicator(closePrice, 10);
        ema.getValue(19);

        addBar(20);
        assertNotNull(ema.getValue(20));
    }

    // ─── weak reference cleanup ───

    @Test
    public void weakReference_cleanedUpOnGC() {
        RecordingListener weakListener = new RecordingListener();
        series.addListener(weakListener);
        addBar(0);
        assertEquals(1, weakListener.added.size());

        weakListener = null;
        System.gc();

        addBar(1); // should not throw
    }

    // ─── helpers ───

    private void addBar(int dayOffset) {
        series.addBar(createBar(dayOffset));
    }

    private Bar createBar(int dayOffset) {
        return new TimeBarBuilder(numFactory)
                .timePeriod(Duration.ofDays(1))
                .endTime(baseTime.plus(Duration.ofDays(dayOffset)))
                .openPrice(numFactory.numOf(100 + dayOffset))
                .highPrice(numFactory.numOf(105 + dayOffset))
                .lowPrice(numFactory.numOf(95 + dayOffset))
                .closePrice(numFactory.numOf(102 + dayOffset))
                .volume(numFactory.numOf(1000))
                .build();
    }

    static class RecordingListener implements BarSeriesListener {
        final List<Integer> added = new ArrayList<>();
        final List<Integer> replaced = new ArrayList<>();
        final List<Bar> addedBars = new ArrayList<>();
        final List<Bar> replacedBars = new ArrayList<>();

        @Override
        public void onBarAdded(int index, Bar bar) {
            added.add(index);
            addedBars.add(bar);
        }

        @Override
        public void onBarReplaced(int index, Bar bar) {
            replaced.add(index);
            replacedBars.add(bar);
        }
    }
}
