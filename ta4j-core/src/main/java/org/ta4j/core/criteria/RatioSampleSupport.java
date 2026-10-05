/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.Duration;
import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradeFill;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.IndexPair;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexes;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.utils.BarSeriesUtils;
import org.ta4j.core.utils.TimeConstants;

final class RatioSampleSupport {

    private RatioSampleSupport() {
    }

    static Stream<Sample> samples(BarSeries series, TradingRecord tradingRecord, SamplingFrequency samplingFrequency,
            ZoneId groupingZoneId, ExcessReturns excessReturns, OpenPositionHandling openPositionHandling) {
        Objects.requireNonNull(series, "series must not be null");
        Objects.requireNonNull(tradingRecord, "tradingRecord must not be null");
        Objects.requireNonNull(samplingFrequency, "samplingFrequency must not be null");
        Objects.requireNonNull(groupingZoneId, "groupingZoneId must not be null");
        Objects.requireNonNull(excessReturns, "excessReturns must not be null");
        Objects.requireNonNull(openPositionHandling, "openPositionHandling must not be null");

        if (samplingFrequency == SamplingFrequency.TRADE) {
            return tradeSamples(series, tradingRecord, excessReturns, openPositionHandling);
        }
        return timeBasedSamples(series, samplingFrequency, groupingZoneId, excessReturns);
    }

    private static Stream<Sample> timeBasedSamples(BarSeries series, SamplingFrequency samplingFrequency,
            ZoneId groupingZoneId, ExcessReturns excessReturns) {
        int beginIndex = excessReturns.getBeginIndex();
        boolean includeInitialReturn = excessReturns.hasInitialReturn();
        int startIndex = includeInitialReturn ? beginIndex : beginIndex + 1;
        int anchorIndex = includeInitialReturn ? beginIndex - 1 : beginIndex;
        int endIndex = excessReturns.getEndIndex();
        SamplingFrequencyIndexes samplingFrequencyIndexes = new SamplingFrequencyIndexes(samplingFrequency,
                groupingZoneId);
        if (includeInitialReturn && beginIndex == endIndex) {
            return Stream.of(toSample(series, new IndexPair(anchorIndex, beginIndex), excessReturns));
        }
        return samplingFrequencyIndexes.sample(excessReturns::getCapturedEndTime, anchorIndex, startIndex, endIndex)
                .map(indexPair -> toSample(series, indexPair, excessReturns));
    }

    private static Stream<Sample> tradeSamples(BarSeries series, TradingRecord tradingRecord,
            ExcessReturns excessReturns, OpenPositionHandling openPositionHandling) {
        int beginIndex = excessReturns.getBeginIndex();
        int finalIndex = excessReturns.getEndIndex();
        return tradePairs(tradingRecord, beginIndex, finalIndex, excessReturns, openPositionHandling)
                .map(indexPair -> toSample(series, indexPair, excessReturns));
    }

    private static Stream<IndexPair> tradePairs(TradingRecord tradingRecord, int beginIndex, int finalIndex,
            ExcessReturns excessReturns, OpenPositionHandling openPositionHandling) {
        List<Position> positions = tradingRecord.getPositions()
                .stream()
                .filter(position -> openPositionHandling != OpenPositionHandling.IGNORE || position != null
                        && position.getExit() != null && position.getExit().getIndex() <= finalIndex)
                .toList();
        Stream<IndexPair> closedPairs = positions.stream()
                .map(position -> toTradePair(position, beginIndex, finalIndex, excessReturns, openPositionHandling))
                .filter(Objects::nonNull);
        if (openPositionHandling == OpenPositionHandling.IGNORE) {
            return closedPairs;
        }
        Stream<Position> openPositions = openPositions(tradingRecord).stream()
                .filter(position -> !positions.contains(position));
        return Stream.concat(closedPairs,
                openPositions.map(
                        position -> toTradePair(position, beginIndex, finalIndex, excessReturns, openPositionHandling))
                        .filter(Objects::nonNull));
    }

    private static List<Position> openPositions(TradingRecord tradingRecord) {
        List<Position> openPositions = tradingRecord.getOpenPositions();
        if (!openPositions.isEmpty()) {
            return openPositions;
        }
        Position currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition != null && currentPosition.isOpened()) {
            return List.of(currentPosition);
        }
        return List.of();
    }

    private static IndexPair toTradePair(Position position, int beginIndex, int finalIndex, ExcessReturns excessReturns,
            OpenPositionHandling openPositionHandling) {
        if (position == null) {
            return null;
        }
        Trade entry = position.getEntry();
        int entryFillIndex = entry == null ? -1 : firstExecutedFillIndex(entry, finalIndex);
        if (entryFillIndex < 0) {
            return null;
        }
        int entryIndex = Math.max(entryFillIndex, beginIndex);
        if (position.getFuturesContract() != null && entryFillIndex >= beginIndex
                && (entryFillIndex > beginIndex || excessReturns.hasInitialReturn())) {
            entryIndex = entryFillIndex - 1;
        }
        int currentIndex = finalIndex;
        Trade exit = position.getExit();
        if (exit != null) {
            currentIndex = lastExecutedFillIndex(exit, finalIndex);
            if (currentIndex < 0) {
                if (openPositionHandling != OpenPositionHandling.MARK_TO_MARKET) {
                    return null;
                }
                currentIndex = finalIndex;
            }
            if (openPositionHandling == OpenPositionHandling.MARK_TO_MARKET
                    && hasResidualExposure(position, finalIndex)) {
                currentIndex = finalIndex;
            }
        }
        if (currentIndex < entryIndex) {
            return null;
        }
        if (entry.getIndex() < beginIndex && currentIndex == entryIndex) {
            // Entered before the retained window and exited on its first bar:
            // nothing of the trade remains observable, unlike a genuine
            // zero-duration trade entered on that bar.
            return null;
        }
        return new IndexPair(entryIndex, currentIndex);
    }

    private static boolean hasResidualExposure(Position position, int finalIndex) {
        if (position.getEntry() == null || position.getEntry().getIndex() > finalIndex) {
            return false;
        }
        Trade entry = position.getEntry();
        Num entryAmount = executedAmount(entry, finalIndex);
        if (!entryAmount.isPositive()) {
            return false;
        }
        Trade exit = position.getExit();
        return exit == null || entryAmount.isGreaterThan(executedAmount(exit, finalIndex));
    }

    private static Num executedAmount(Trade trade, int finalIndex) {
        NumFactory numFactory = trade.getAmount().getNumFactory();
        Num amount = numFactory.zero();
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                amount = amount.plus(numFactory.numOf(fill.amount().getDelegate()));
            }
        }
        return amount;
    }

    private static int firstExecutedFillIndex(Trade trade, int finalIndex) {
        int firstIndex = -1;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                firstIndex = firstIndex < 0 ? fill.index() : Math.min(firstIndex, fill.index());
            }
        }
        return firstIndex;
    }

    private static int lastExecutedFillIndex(Trade trade, int finalIndex) {
        int lastIndex = -1;
        for (TradeFill fill : Trade.executionFillsOf(trade)) {
            if (fill.index() >= 0 && fill.index() <= finalIndex) {
                lastIndex = Math.max(lastIndex, fill.index());
            }
        }
        return lastIndex;
    }

    private static Sample toSample(BarSeries series, IndexPair indexPair, ExcessReturns excessReturns) {
        int previousIndex = indexPair.previousIndex();
        int currentIndex = indexPair.currentIndex();
        return new Sample(excessReturns.excessReturn(previousIndex, currentIndex),
                BarSeriesUtils.deltaYears(excessReturns.getCapturedEndTime(previousIndex),
                        excessReturns.getCapturedEndTime(currentIndex), series.numFactory()));
    }
}
