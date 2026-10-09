/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.ArrayDeque;
import java.util.Map;
import org.ta4j.core.Trade;
import java.util.Objects;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseTradingRecord;
import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.AnalysisContext;
import org.ta4j.core.analysis.AnalysisContext.MissingHistoryPolicy;
import org.ta4j.core.analysis.AnalysisContext.PositionInclusionPolicy;
import org.ta4j.core.analysis.AnalysisWindow;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.num.Num;

/**
 * An abstract analysis criterion.
 */
public abstract class AbstractAnalysisCriterion implements AnalysisCriterion {

    @Override
    public Num calculate(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        if (series.isEmpty()) {
            return AnalysisCriterion.super.calculate(series, tradingRecord, window, context);
        }
        return calculate(series, projectTradingRecord(series, tradingRecord, window, context));
    }

    /**
     * Captures the existing explicit-window projection for reuse by criteria that
     * need its resolved record as well as its numeric result.
     *
     * @param series        the source series
     * @param tradingRecord the source record
     * @param window        the requested window
     * @param context       the explicit selection and valuation policy
     * @return the projected record
     * @since 0.26.1
     */
    protected TradingRecord projectTradingRecord(BarSeries series, TradingRecord tradingRecord, AnalysisWindow window,
            AnalysisContext context) {
        ProjectionCaptureCriterion capture = new ProjectionCaptureCriterion();
        capture.calculate(series, tradingRecord, window, context);
        return new SelectedTradingRecord(capture.record);
    }

    /**
     * Resolves a directly bounded record to its fully contained closed population.
     * Explicit-window projections have already selected their policy and pass
     * through unchanged, including when used by composed criteria. Records without
     * explicit bounds retain their historical behavior.
     *
     * @param series        the series used to resolve available bounds
     * @param tradingRecord the source record
     * @return the selected record
     * @since 0.26.1
     */
    protected TradingRecord boundedTradingRecord(BarSeries series, TradingRecord tradingRecord) {
        return boundedTradingRecord(series, tradingRecord, OpenPositionHandling.IGNORE);
    }

    /**
     * Resolves a directly bounded record using the existing window projection and
     * the criterion's open-position policy. Explicit-window context takes
     * precedence.
     *
     * @param series               the series used to resolve available bounds
     * @param tradingRecord        the source record
     * @param openPositionHandling handling of positions open at the logical end
     * @return the selected record
     * @since 0.26.1
     */
    protected TradingRecord boundedTradingRecord(BarSeries series, TradingRecord tradingRecord,
            OpenPositionHandling openPositionHandling) {
        if (tradingRecord instanceof SelectedTradingRecord
                || tradingRecord.getStartIndex() == null && tradingRecord.getEndIndex() == null) {
            return tradingRecord;
        }
        int start = tradingRecord.getStartIndex(series);
        int end = tradingRecord.getEndIndex(series);
        if (series.isEmpty() || start > end) {
            return new SelectedTradingRecord(tradingRecord, start, end);
        }
        AnalysisContext context = AnalysisContext.defaults()
                .withMissingHistoryPolicy(MissingHistoryPolicy.CLAMP)
                .withPositionInclusionPolicy(PositionInclusionPolicy.FULLY_CONTAINED)
                .withOpenPositionHandling(Objects.requireNonNull(openPositionHandling, "openPositionHandling"));
        SelectedTradingRecord selected = (SelectedTradingRecord) projectTradingRecord(series, tradingRecord,
                AnalysisWindow.barRange(start, end), context);
        // Projection owns membership and valuation; direct calls retain source order
        // and original closed-position snapshots for position-level consumers.
        Map<Trade, ArrayDeque<Position>> byEntry = new HashMap<>();
        for (Position position : selected.getPositions()) {
            byEntry.computeIfAbsent(position.getEntry(), entry -> new ArrayDeque<>()).add(position);
        }
        List<Position> ordered = new ArrayList<>();
        for (Position position : tradingRecord.getPositions()) {
            ArrayDeque<Position> matches = byEntry.get(position.getEntry());
            if (matches != null && !matches.isEmpty()) {
                Position projected = matches.removeFirst();
                ordered.add(position.isClosed() && position.getExit().getIndex() <= end ? position : projected);
            }
        }
        for (Position position : selected.getPositions()) {
            ArrayDeque<Position> matches = byEntry.get(position.getEntry());
            if (matches != null && matches.remove(position)) {
                ordered.add(position);
            }
        }
        selected.positions = List.copyOf(ordered);
        return selected;
    }

    /**
     * Selects the bounded population while retaining native open entries, without
     * inventing an exit trade or exit fee. Used by fee and curve consumers whose
     * native open valuation differs from a synthetic window-end close.
     *
     * @param series        the series used to resolve bounds
     * @param tradingRecord the source record
     * @return the selected record with entries open at its logical end
     * @since 0.26.1
     */
    protected TradingRecord boundedTradingRecordWithOpenEntries(BarSeries series, TradingRecord tradingRecord) {
        TradingRecord selected = boundedTradingRecord(series, tradingRecord);
        if (selected == tradingRecord || series.isEmpty()) {
            return selected;
        }
        SelectedTradingRecord result = (SelectedTradingRecord) selected;
        int start = result.getStartIndex(series);
        int end = result.getEndIndex(series);
        if (start > end) {
            return result;
        }
        for (Position position : tradingRecord.getPositions()) {
            if (position.isClosed() && position.getExit().getIndex() > end) {
                appendOpenEntry(result, position, start, end);
            }
        }
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (openPositions != null && !openPositions.isEmpty()) {
            for (Position position : openPositions) {
                appendOpenEntry(result, position, start, end);
            }
        } else {
            appendOpenEntry(result, tradingRecord.getCurrentPosition(), start, end);
        }
        return result;
    }

    private static void appendOpenEntry(SelectedTradingRecord target, Position position, int start, int end) {
        if (position != null && position.getEntry() != null && position.getEntry().getIndex() >= start
                && position.getEntry().getIndex() <= end) {
            target.operate(position.getEntry());
        }
    }

    private static final class SelectedTradingRecord extends BaseTradingRecord {
        private List<Position> positions = List.of();

        @Override
        public List<Position> getPositions() {
            return positions;
        }

        private SelectedTradingRecord(TradingRecord source, Integer start, Integer end) {
            super(source.getStartingType(), start, end, source.getTransactionCostModel(), source.getHoldingCostModel());
        }

        private SelectedTradingRecord(TradingRecord source) {
            this(source, source.getStartIndex(), source.getEndIndex());
            positions = List.copyOf(source.getPositions());
            for (Position position : positions) {
                operate(position.getEntry());
                operate(position.getExit());
            }
        }
    }

    private static final class ProjectionCaptureCriterion implements AnalysisCriterion {
        private TradingRecord record;

        @Override
        public Num calculate(BarSeries series, TradingRecord tradingRecord) {
            record = tradingRecord;
            return series.numFactory().zero();
        }

        @Override
        public Num calculate(BarSeries series, Position position) {
            return series.numFactory().zero();
        }

        @Override
        public boolean betterThan(Num first, Num second) {
            return false;
        }
    }

    /**
     * Returns the return representation used by this criterion, if applicable.
     * <p>
     * Criteria that use {@link ReturnRepresentation} should override this method to
     * return their representation. Criteria that don't use return representations
     * should not override this method (it defaults to empty).
     *
     * @return the return representation, or empty if this criterion doesn't use
     *         return representations
     */
    public Optional<ReturnRepresentation> getReturnRepresentation() {
        return Optional.empty();
    }

    @Override
    public String toString() {
        String[] tokens = getClass().getSimpleName().split("(?=\\p{Lu})", -1);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < tokens.length - 1; i++) {
            sb.append(tokens[i]).append(' ');
        }
        return sb.toString().trim();
    }
}
