/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria;

import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;
import org.ta4j.core.*;
import org.ta4j.core.Trade.TradeType;
import org.ta4j.core.analysis.ExcessReturns;
import org.ta4j.core.analysis.OpenPositionHandling;
import org.ta4j.core.analysis.cost.CostModel;
import org.ta4j.core.analysis.cost.ZeroCostModel;
import org.ta4j.core.analysis.frequency.IndexPair;
import org.ta4j.core.analysis.frequency.Sample;
import org.ta4j.core.analysis.frequency.SamplingFrequency;
import org.ta4j.core.analysis.frequency.SamplingFrequencyIndexes;
import org.ta4j.core.utils.BarSeriesUtils;

/**
 * Generates ratio-criterion samples for time-based and trade-based sampling.
 *
 * <p>
 * Time-based sampling uses {@link SamplingFrequencyIndexes} to group bar
 * indices by period boundaries. Trade-based sampling emits one sample per
 * position interval (entry to exit, or entry to final index for included open
 * positions). For {@link LiveTradingRecord}, open lots are expanded into
 * independent positions so mark-to-market trade sampling mirrors lot-level
 * analysis behavior.
 *
 * @since 0.22.2
 */
final class RatioSampleSupport {

    private RatioSampleSupport() {
    }

    /**
     * Builds ratio samples according to the selected sampling frequency.
     *
     * @param series               the bar series
     * @param tradingRecord        the trading record
     * @param samplingFrequency    the sampling mode
     * @param groupingZoneId       the grouping time zone for time-based sampling
     * @param excessReturns        excess-return calculator
     * @param openPositionHandling controls inclusion of open positions for trade
     *                             sampling
     * @return a stream of generated samples
     */
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
        var beginIndex = series.getBeginIndex();
        var startIndex = beginIndex + 1;
        var endIndex = series.getEndIndex();
        var samplingFrequencyIndexes = new SamplingFrequencyIndexes(samplingFrequency, groupingZoneId);
        return samplingFrequencyIndexes.sample(series, beginIndex, startIndex, endIndex)
                .map(indexPair -> toSample(series, indexPair, excessReturns));
    }

    private static Stream<Sample> tradeSamples(BarSeries series, TradingRecord tradingRecord,
            ExcessReturns excessReturns, OpenPositionHandling openPositionHandling) {
        var finalIndex = series.getEndIndex();
        return tradePairs(tradingRecord, finalIndex, openPositionHandling)
                .map(indexPair -> toSample(series, indexPair, excessReturns));
    }

    private static Stream<IndexPair> tradePairs(TradingRecord tradingRecord, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        return positionsForTradeSampling(tradingRecord, finalIndex, openPositionHandling)
                .map(position -> toTradePair(position, finalIndex))
                .filter(Objects::nonNull);
    }

    private static IndexPair toTradePair(Position position, int finalIndex) {
        var entry = position.getEntry();
        if (entry == null) {
            return null;
        }
        var entryIndex = entry.getIndex();
        var currentIndex = finalIndex;
        var exit = position.getExit();
        if (exit != null) {
            currentIndex = Math.min(exit.getIndex(), finalIndex);
        }
        if (currentIndex < entryIndex) {
            return null;
        }
        return new IndexPair(entryIndex, currentIndex);
    }

    private static Stream<Position> positionsForTradeSampling(TradingRecord tradingRecord, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        var recordPositions = tradingRecord.getPositions()
                .stream()
                .filter(position -> shouldIncludePosition(position, finalIndex, openPositionHandling));
        if (shouldIncludeOpenPositions(openPositionHandling)) {
            return recordPositions;
        }
        var openPositions = openPositions(tradingRecord, finalIndex).stream();
        return Stream.concat(recordPositions, openPositions);
    }

    private static boolean shouldIncludePosition(Position position, int finalIndex,
            OpenPositionHandling openPositionHandling) {
        if (position == null || position.getEntry() == null) {
            return false;
        }
        var entryIndex = position.getEntry().getIndex();
        if (entryIndex > finalIndex) {
            return false;
        }
        if (shouldIncludeOpenPositions(openPositionHandling)) {
            var exit = position.getExit();
            var isOpenAtFinalIndex = exit == null || exit.getIndex() > finalIndex;
            return !isOpenAtFinalIndex;
        }
        return true;
    }

    private static boolean shouldIncludeOpenPositions(OpenPositionHandling openPositionHandling) {
        return openPositionHandling != OpenPositionHandling.MARK_TO_MARKET;
    }

    private static List<Position> openPositions(TradingRecord tradingRecord, int finalIndex) {
        if (tradingRecord instanceof LiveTradingRecord liveTradingRecord) {
            return openPositionsFromLiveRecord(liveTradingRecord, finalIndex);
        }
        var positions = new ArrayList<Position>();
        var currentPosition = tradingRecord.getCurrentPosition();
        if (currentPosition != null && currentPosition.isOpened() && currentPosition.getEntry() != null
                && currentPosition.getEntry().getIndex() <= finalIndex) {
            positions.add(currentPosition);
        }
        return positions;
    }

    private static List<Position> openPositionsFromLiveRecord(LiveTradingRecord record, int finalIndex) {
        var positions = new ArrayList<Position>();
        var transactionCostModel = defaultCostModel(record.getTransactionCostModel());
        var holdingCostModel = defaultCostModel(record.getHoldingCostModel());
        var startingType = record.getStartingType();
        var entrySide = startingType == TradeType.BUY ? ExecutionSide.BUY : ExecutionSide.SELL;
        for (var openPosition : record.getOpenPositions()) {
            for (var lot : openPosition.lots()) {
                if (lot.entryIndex() > finalIndex) {
                    continue;
                }
                var entryTrade = new LiveTrade(lot.entryIndex(), lot.entryTime(), lot.entryPrice(), lot.amount(),
                        lot.fee(), entrySide, lot.orderId(), lot.correlationId());
                var position = new Position(entryTrade, transactionCostModel, holdingCostModel);
                positions.add(position);
            }
        }
        return positions;
    }

    private static CostModel defaultCostModel(CostModel costModel) {
        return costModel == null ? new ZeroCostModel() : costModel;
    }

    private static Sample toSample(BarSeries series, IndexPair indexPair, ExcessReturns excessReturns) {
        var previousIndex = indexPair.previousIndex();
        var currentIndex = indexPair.currentIndex();
        return new Sample(excessReturns.excessReturn(previousIndex, currentIndex),
                BarSeriesUtils.deltaYears(series, previousIndex, currentIndex));
    }
}
