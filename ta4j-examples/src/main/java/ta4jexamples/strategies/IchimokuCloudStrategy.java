/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2021 Ta4j Organization & respective
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
package ta4jexamples.strategies;

import static org.ta4j.core.indicators.helpers.CombineIndicator.max;

import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Indicator;
import org.ta4j.core.Rule;
import org.ta4j.core.Strategy;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.UnstableIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.PreviousValueIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuChikouSpanIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuKijunSenIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanAIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuSenkouSpanBIndicator;
import org.ta4j.core.indicators.ichimoku.IchimokuTenkanSenIndicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.rules.CrossedDownIndicatorRule;
import org.ta4j.core.rules.CrossedUpIndicatorRule;
import org.ta4j.core.rules.OverIndicatorRule;

import ta4jexamples.loaders.CsvTradesLoader;

public class IchimokuCloudStrategy {

    private static Strategy buildStrategy(BarSeries series) {
        int tenkanSenBarcount = 9;
        int kijunSenBarCount = 26;
        int senkunSpanBarCount = kijunSenBarCount * 2; // 52
        int chikouSpanDelay = kijunSenBarCount; // 26

        // create all the indicators
        IchimokuIndicators ichimokuIndicators = new IchimokuIndicators(series, tenkanSenBarcount, kijunSenBarCount,
                senkunSpanBarCount, chikouSpanDelay);

        // create the entry rule (its just an easy example strategy. The internet is
        // full of ideas how to use the ichimoku idnocators)
        Rule longPositionEntryRule = createIchimokuLongEntryRule(series, chikouSpanDelay, ichimokuIndicators);

        // create the exit rule (its just an easy example strategy. The internet is full
        // of ideas how to use the ichimoku idnocators)
        Rule longPositionExitRule = createIchimokuLongExitRule(ichimokuIndicators);

        // create the example ichimoku strategy
        return new BaseStrategy(longPositionEntryRule, longPositionExitRule);

    }

    private static Rule createIchimokuLongExitRule(IchimokuIndicators ichimokuIndicators) {
        Rule conversionLineCrossesBackBaseline = new CrossedDownIndicatorRule(ichimokuIndicators.tenkanSenIndicator,
                ichimokuIndicators.kijunSenIndicator);
        return conversionLineCrossesBackBaseline; // This is a very basic example exit. Test your own exit criterias
                                                  // here, dont forget stop loss if needed!
    }

    private static Rule createIchimokuLongEntryRule(BarSeries series, int chikouSpanDelay,
            IchimokuIndicators ichimokuIndicators) {
        // signal 1: cloud in future is green --> uptrend
        OverIndicatorRule cloudGreenInFuture = new OverIndicatorRule(ichimokuIndicators.senkouSpanAFutureIndicator,
                ichimokuIndicators.senkouSpanBFutureIndicator);

        // signal 2: conversion line crosses the base line above the cloud
        // --> bounce back to resistance of the cloud and breakout again up. Evertything
        // in an uptrend as it was over the cloud --> Strong buy signal
        Rule conversionLineCrossesBaseLine = new CrossedUpIndicatorRule(ichimokuIndicators.tenkanSenIndicator,
                ichimokuIndicators.kijunSenIndicator);
        Indicator<Num> cloudInPresentUpperLine = max(ichimokuIndicators.senkouSpanAPresentIndicator,
                ichimokuIndicators.senkouSpanBPresentIndicator);
        Rule conversionLineCrossIsOverCloud = new OverIndicatorRule(ichimokuIndicators.kijunSenIndicator,
                cloudInPresentUpperLine).and(conversionLineCrossesBaseLine);

        // signal 3: the lagging span is over the past price of the market. Sediment and
        // also a sign for uptrent
        ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
        Indicator<Num> delayedMarketPrice = new UnstableIndicator(
                new PreviousValueIndicator(closePrice, chikouSpanDelay), chikouSpanDelay);
        Rule laggingSpanAbovePastPrice = new OverIndicatorRule(ichimokuIndicators.chikouSpanIndicator,
                delayedMarketPrice);

        // signal 4: We are not down moving --> the current price must also be over the
        // conversion line
        Rule priceAboveTheCloud = new OverIndicatorRule(closePrice, cloudInPresentUpperLine);
        Rule priceAboveConversionLine = new OverIndicatorRule(closePrice, ichimokuIndicators.tenkanSenIndicator);

        // put everything together to a entry rule (Long position)
        Rule entryRule = priceAboveTheCloud.and(cloudGreenInFuture)
                .and(conversionLineCrossIsOverCloud)
                .and(laggingSpanAbovePastPrice)
                .and(priceAboveConversionLine);
        return entryRule;
    }

    public static void main(String[] args) {

        // Getting the bar series
        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        // Building the trading strategy
        Strategy strategy = buildStrategy(series);

        // Running the strategy
        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(strategy);
        System.out.println("Number of positions for the strategy: " + tradingRecord.getPositionCount());

        // Analysis
        System.out.println(
                "Total return for the strategy: " + new GrossReturnCriterion().calculate(series, tradingRecord));
    }

    private static class IchimokuIndicators {
        public final IchimokuKijunSenIndicator kijunSenIndicator;
        public final IchimokuTenkanSenIndicator tenkanSenIndicator;
        public final IchimokuChikouSpanIndicator chikouSpanIndicator;
        public final IchimokuSenkouSpanAIndicator senkouSpanAFutureIndicator;
        public final Indicator<Num> senkouSpanAPresentIndicator;
        public final Indicator<Num> senkouSpanAPastIndicator;
        public final IchimokuSenkouSpanBIndicator senkouSpanBFutureIndicator;
        public final Indicator<Num> senkouSpanBPresentIndicator;
        public final Indicator<Num> senkouSpanBPastIndicator;

        public IchimokuIndicators(BarSeries series, int tenkanSenBarcount, int kijunSenBarCount, int senkunSpanBarCount,
                int chikouSpanDelay) {
            if (chikouSpanDelay != senkunSpanBarCount / 2) {
                throw new IllegalArgumentException(
                        "The senkunSpan delay should always be the double of the chikou span delay to allow correct calculations for the past");
            }
            tenkanSenIndicator = new IchimokuTenkanSenIndicator(series, tenkanSenBarcount);
            kijunSenIndicator = new IchimokuKijunSenIndicator(series, kijunSenBarCount);
            chikouSpanIndicator = new IchimokuChikouSpanIndicator(series);
            senkouSpanAFutureIndicator = new IchimokuSenkouSpanAIndicator(tenkanSenIndicator, kijunSenIndicator);
            senkouSpanBFutureIndicator = new IchimokuSenkouSpanBIndicator(series, senkunSpanBarCount);

            senkouSpanAPresentIndicator = createDelayedIndicator(senkouSpanAFutureIndicator, senkunSpanBarCount / 2);
            senkouSpanAPastIndicator = createDelayedIndicator(senkouSpanAFutureIndicator, senkunSpanBarCount);
            senkouSpanBPresentIndicator = createDelayedIndicator(senkouSpanBFutureIndicator, senkunSpanBarCount / 2);
            senkouSpanBPastIndicator = createDelayedIndicator(senkouSpanBFutureIndicator, senkunSpanBarCount);
        }

        private Indicator<Num> createDelayedIndicator(Indicator<Num> indicator, int delay) {
            return new UnstableIndicator(new PreviousValueIndicator(indicator, delay), delay);
        }
    }
}
