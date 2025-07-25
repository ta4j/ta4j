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
package org.ta4j.core.rules;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.ta4j.core.Position;
import org.ta4j.core.TradingRecord;

/**
 * Satisfied when the aggregation of the positions from the tradingRecord
 * executed up to the {@code index} is within the range of a minimum or maximum
 * required value. It includes positions where the {@code entry} and
 * {@code exit} is within the {@code index}.
 */
public class PositionRule extends AbstractRule {

    /** Filter to differentiate between open and closed positions. */
    public enum PositionFilter {

        /** Consider all positions. */
        ALL {
            @Override
            public List<Position> apply(TradingRecord tradingRecord) {
                var all = new ArrayList<>(tradingRecord.getPositions());
                var currentPosition = tradingRecord.getCurrentPosition();
                if (currentPosition.isOpened()) {
                    all.add(currentPosition);
                }
                return all;
            }
        },

        /** Consider only open position. */
        OPEN {
            @Override
            public List<Position> apply(TradingRecord tradingRecord) {
                var currentPosition = tradingRecord.getCurrentPosition();
                return currentPosition.isOpened() ? List.of(currentPosition) : List.of();
            }
        },

        /** Consider only closed positions. */
        CLOSED {
            @Override
            public List<Position> apply(TradingRecord tradingRecord) {
                return new ArrayList<>(tradingRecord.getPositions());
            }
        };

        /**
         * @param tradingRecord
         * @return filtered positions
         */
        public abstract List<Position> apply(final TradingRecord tradingRecord);
    }

    /** The aggregation type for position accumulation. */
    public enum PositionAggregationType {

        /** Aggregates the number of positions. */
        NUMBER_OF_POSITIONS {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                var count = positions.stream().filter(p -> isEntryWithinIndex(index, p)).count();
                return BigDecimal.valueOf(count);
            }
        },

        /** Aggregates the number of entry trades. */
        NUMBER_OF_ENTRY_TRADES {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                var count = positions.stream()
                        .filter(p -> isEntryWithinIndex(index, p))
                        .map(Position::getEntry)
                        .count();
                return BigDecimal.valueOf(count);
            }
        },

        /** Aggregates the number of exit trades. */
        NUMBER_OF_EXIT_TRADES {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                var count = positions.stream().filter(p -> isExitWithinIndex(index, p)).map(Position::getExit).count();
                return BigDecimal.valueOf(count);
            }
        },

        /** Aggregates the entry amount of each position. */
        AMOUNT {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                return positions.stream()
                        .filter(p -> isPositionWithinIndex(index, p))
                        .map(p -> p.getEntry().getAmount().bigDecimalValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        },

        /** Aggregates the entry value (price x amount) of each position. */
        VALUE {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                return positions.stream()
                        .filter(p -> isPositionWithinIndex(index, p))
                        .map(p -> p.getEntry().getValue().bigDecimalValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        },

        /** Aggregates the {@link Position#getProfit()} of each position. */
        NET_PROFIT {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                return positions.stream()
                        .filter(p -> isPositionWithinIndex(index, p))
                        .map(p -> p.getProfit().bigDecimalValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        },

        /** Aggregates the {@link Position#getGrossProfit()} of each position. */
        GROSS_PROFIT {
            @Override
            public BigDecimal apply(int index, List<Position> positions) {
                return positions.stream()
                        .filter(p -> isPositionWithinIndex(index, p))
                        .map(p -> p.getGrossProfit().bigDecimalValue())
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
        }

        ;

        /**
         * @param index     the bar index
         * @param positions
         * @return result of aggregation
         */
        public abstract BigDecimal apply(int index, List<Position> positions);

        /**
         * @param index    the bar index
         * @param position
         * @return true if position is within the index
         */
        public static boolean isPositionWithinIndex(int index, Position position) {
            return isEntryWithinIndex(index, position) && isExitWithinIndex(index, position);
        }

        /**
         * @param index    the bar index
         * @param position
         * @return true if {@link Position#getEntry()} is within the index
         */
        public static boolean isEntryWithinIndex(int index, Position position) {
            var entry = position.getEntry();
            return entry != null && entry.getIndex() <= index;
        }

        /**
         * @param index    the bar index
         * @param position
         * @return true if {@link Position#getExit()} is within the index
         */
        public static boolean isExitWithinIndex(int index, Position position) {
            var exit = position.getExit();
            return exit == null || exit.getIndex() <= index;
        }
    }

    private final PositionFilter positionFilter;
    private final PositionAggregationType aggregationType;
    private final BigDecimal requiredMinimum;
    private final BigDecimal requiredMaximum;

    /**
     * Constructor.
     *
     * @param positionFilter
     * @param aggregationType
     * @param requiredMinimum the aggregated value of the positions that the
     *                        TradingRecord must at least have; optional if
     *                        {@code requiredMaximum} is specified.
     * @param requiredMaximum the aggregated value of the positions that the
     *                        TradingRecord must not exceed; optional if
     *                        {@code requiredMinimum} is specified.
     * @throws NullPointerException     if {@code positionFilter} is {@code null}
     * @throws NullPointerException     if {@code aggregationType} is {@code null}
     * @throws IllegalArgumentException if both {@code requiredMinimum} and
     *                                  {@code requiredMaximum} are {@code null}
     */
    public PositionRule(PositionFilter positionFilter, PositionAggregationType aggregationType,
            BigDecimal requiredMinimum, BigDecimal requiredMaximum) {
        this.positionFilter = Objects.requireNonNull(positionFilter);
        this.aggregationType = Objects.requireNonNull(aggregationType);
        this.requiredMinimum = requiredMinimum;
        this.requiredMaximum = requiredMaximum;

        if (requiredMinimum == null && requiredMaximum == null) {
            throw new IllegalArgumentException("A required minimum or maximum must be specified.");
        }
    }

    /** This rule uses the {@code tradingRecord}. */
    @Override
    public boolean isSatisfied(int index, TradingRecord tradingRecord) {

        var minimumSatisfied = requiredMinimum == null || requiredMinimum == BigDecimal.ZERO;
        var maximumSatisfied = requiredMaximum == null || requiredMaximum == BigDecimal.ZERO;
        boolean satisfied = minimumSatisfied && maximumSatisfied;

        if (tradingRecord != null && !tradingRecord.getTrades().isEmpty()) {
            List<Position> positions = positionFilter.apply(tradingRecord);
            var aggregation = aggregationType.apply(index, positions);
            minimumSatisfied = requiredMinimum == null || requiredMinimum.compareTo(aggregation) <= 0;
            maximumSatisfied = requiredMaximum == null || requiredMaximum.compareTo(aggregation) >= 0;
            satisfied = minimumSatisfied && maximumSatisfied;
        }

        traceIsSatisfied(index, satisfied);
        return satisfied;
    }
}
