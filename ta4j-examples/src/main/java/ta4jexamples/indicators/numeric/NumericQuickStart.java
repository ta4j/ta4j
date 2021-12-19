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
package ta4jexamples.indicators.numeric;

import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Rule;
import org.ta4j.core.TradingRecord;
import org.ta4j.core.criteria.ReturnOverMaxDrawdownCriterion;
import org.ta4j.core.criteria.VersusBuyAndHoldCriterion;
import org.ta4j.core.criteria.WinningPositionsRatioCriterion;
import org.ta4j.core.criteria.pnl.GrossReturnCriterion;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.numeric.NumericIndicator;
import org.ta4j.core.rules.StopGainRule;
import org.ta4j.core.rules.StopLossRule;

import ta4jexamples.loaders.CsvTradesLoader;

/**
 * A simple demo that simplifies the original quick start.
 * 
 **/
public class NumericQuickStart {

    public static void main(String[] args) {

        BarSeries series = CsvTradesLoader.loadBitstampSeries();

        // the numeric indicator makes it easier to code rules

        NumericIndicator closePrice = NumericIndicator.closePrice(series);
        NumericIndicator shortSma = closePrice.sma(5);
        NumericIndicator longSma = closePrice.sma(30);

        Rule buyingRule = shortSma.crossedOver(longSma).or(closePrice.crossedUnder(800));

        // the stop loss and stop gain rules need an actual ClosePriceIndicator object
        // perhaps we can do something to make this part more fluent

        ClosePriceIndicator closePriceIndicator = new ClosePriceIndicator(series);
        Rule sellingRule = shortSma.crossedUnder(longSma)
                .or(new StopLossRule(closePriceIndicator, series.numOf(3)))
                .or(new StopGainRule(closePriceIndicator, series.numOf(2)));

        BarSeriesManager seriesManager = new BarSeriesManager(series);
        TradingRecord tradingRecord = seriesManager.run(new BaseStrategy(buyingRule, sellingRule));
        System.out.println("Number of positions for our strategy: " + tradingRecord.getPositionCount());

        // Analysis

        // Getting the winning positions ratio
        AnalysisCriterion winningPositionsRatio = new WinningPositionsRatioCriterion();
        System.out.println("Winning positions ratio: " + winningPositionsRatio.calculate(series, tradingRecord));
        // Getting a risk-reward ratio
        AnalysisCriterion romad = new ReturnOverMaxDrawdownCriterion();
        System.out.println("Return over Max Drawdown: " + romad.calculate(series, tradingRecord));

        // Total return of our strategy vs total return of a buy-and-hold strategy
        AnalysisCriterion vsBuyAndHold = new VersusBuyAndHoldCriterion(new GrossReturnCriterion());
        System.out.println("Our return vs buy-and-hold return: " + vsBuyAndHold.calculate(series, tradingRecord));

        // Your turn!

    }

}
