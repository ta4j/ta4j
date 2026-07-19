/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.ZoneId;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.Trade;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.frequency.IndexPair;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexes;
import org.ta4j.core.utils.BarSeriesUtils;

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
        int beginIndex = series.getBeginIndex();
        int startIndex = beginIndex + 1;
        int endIndex = series.getEndIndex();
        SamplingFrequencyIndexes samplingFrequencyIndexes = new SamplingFrequencyIndexes(samplingFrequency,
                groupingZoneId);
        return samplingFrequencyIndexes.sample(series, beginIndex, startIndex, endIndex)
                .map(indexPair -> toSample(series, indexPair, excessReturns));
    }

    private static Stream<Sample> tradeSamples(BarSeries series, TradingRecord tradingRecord,
            ExcessReturns excessReturns, OpenPositionHandling openPositionHandling) {
        int finalIndex = series.getEndIndex();
        return tradePairs(tradingRecord, finalIndex, openPositionHandling)
                .map(indexPair -> toSample(series, indexPair, excessReturns));
    }

    private static Stream<IndexPair> tradePairs(TradingRecord tradingRecord, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        Stream<IndexPair> closedPairs = tradingRecord.getPositions()
                .stream()
                .map(position -> toTradePair(position, finalIndex))
                .filter(Objects::nonNull);
        if (openPositionHandling == OpenPositionHandling.IGNORE) {
            return closedPairs;
        }
        Stream<Position> openPositions = openPositions(tradingRecord).stream();
        return Stream.concat(closedPairs,
                openPositions.map(position -> toTradePair(position, finalIndex)).filter(Objects::nonNull));
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

    private static IndexPair toTradePair(Position position, int finalIndex) {
        if (position == null) {
            return null;
        }
        Trade entry = position.getEntry();
        if (entry == null || entry.getIndex() > finalIndex) {
            return null;
        }
        int entryIndex = entry.getIndex();
        int currentIndex = finalIndex;
        Trade exit = position.getExit();
        if (exit != null) {
            currentIndex = Math.min(exit.getIndex(), finalIndex);
        }
        if (currentIndex < entryIndex) {
            return null;
        }
        return new IndexPair(entryIndex, currentIndex);
    }

    private static Sample toSample(BarSeries series, IndexPair indexPair, ExcessReturns excessReturns) {
        int previousIndex = indexPair.previousIndex();
        int currentIndex = indexPair.currentIndex();
        return new Sample(excessReturns.excessReturn(previousIndex, currentIndex),
                BarSeriesUtils.deltaYears(series, previousIndex, currentIndex));
    }
}
