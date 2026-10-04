/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis.elliott.swing;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.num.Num;

/**
 * Immutable bar-only history observation shared by the detector owners. Capture
 * and verification run in the series read scope; this helper never evaluates an
 * indicator or controls a detector's graph. Supported revisions retain only
 * constant-size metadata, while revisionless series retain consumed H/L/C
 * values.
 */
record SwingHistorySnapshot(long revision, int beginIndex, int endIndex, Bar firstBar, Bar lastBar,
        List<BarState> bars) {

    SwingHistorySnapshot {
        bars = List.copyOf(bars);
    }

    static SwingHistorySnapshot capture(final BarSeries series) {
        final int begin = series.getBeginIndex();
        final int end = series.isEmpty() ? begin - 1 : series.getEndIndex();
        final long revision = series.getBarHistoryRevision();
        if (end < begin) {
            return new SwingHistorySnapshot(revision, begin, end, null, null, List.of());
        }
        final Bar first = series.getBar(begin);
        final Bar last = series.getBar(end);
        if (revision >= 0L) {
            // No retained-window scan or retained-value snapshot allocation.
            return new SwingHistorySnapshot(revision, begin, end, first, last, List.of());
        }
        final List<BarState> bars = new ArrayList<>(end - begin + 1);
        for (long index = begin; index <= end; index++) {
            final Bar bar = series.getBar((int) index);
            bars.add(new BarState(bar, bar.getHighPrice(), bar.getLowPrice(), bar.getClosePrice()));
        }
        return new SwingHistorySnapshot(revision, begin, end, first, last, bars);
    }

    boolean hasChangedIn(final BarSeries series, final boolean afterEvaluation) {
        final int currentBegin = series.getBeginIndex();
        final int currentEnd = series.getEndIndex();
        final long currentRevision = series.getBarHistoryRevision();
        if (currentEnd < endIndex || currentBegin < beginIndex || afterEvaluation && currentBegin != beginIndex
                || currentRevision >= 0L && revision >= 0L && currentRevision != revision
                || (currentRevision < 0L) != (revision < 0L)) {
            return true;
        }
        if (endIndex < beginIndex) {
            return false;
        }
        if (currentBegin <= beginIndex && series.getBar(beginIndex) != firstBar
                || currentEnd == endIndex && series.getBar(currentEnd) != lastBar) {
            return true;
        }
        if (currentRevision < 0L) {
            if (currentBegin != beginIndex) {
                return true;
            }
            // Check the observed prefix after an append, too: identity cannot
            // detect an in-place edit to a retained, already-consumed bar.
            for (int offset = 0; offset < bars.size(); offset++) {
                if (!bars.get(offset).matches(series.getBar(beginIndex + offset))) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Price extrema and ATR consume H/L/C; Num values are immutable. */
    private record BarState(Bar identity, Num high, Num low, Num close) {

        private boolean matches(final Bar bar) {
            return identity == bar && Objects.equals(high, bar.getHighPrice()) && Objects.equals(low, bar.getLowPrice())
                    && Objects.equals(close, bar.getClosePrice());
        }
    }
}
