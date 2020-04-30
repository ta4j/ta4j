/**
 * The MIT License (MIT)
 *
 * Copyright (c) 2014-2017 Marc de Verdelhan, 2017-2020 Ta4j Organization & respective
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
package org.ta4j.core.backtest;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.ta4j.core.TestUtils.assertNumEquals;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.Test;
import org.ta4j.core.AnalysisCriterion;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BarSeriesManager;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.BaseStrategy;
import org.ta4j.core.Strategy;
import org.ta4j.core.analysis.criteria.AbstractCriterionTest;
import org.ta4j.core.analysis.criteria.ProfitLossCriterion;
import org.ta4j.core.indicators.SMAIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.OpenPriceIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;
import org.ta4j.core.trading.rules.OverIndicatorRule;
import org.ta4j.core.trading.rules.UnderIndicatorRule;

public class BacktestExecutorTest extends AbstractCriterionTest {

	public BacktestExecutorTest(Function<Number, Num> numFunction) {
		super((params) -> new ProfitLossCriterion(), numFunction);
	}

	@Test
	public void testChooseBest() {
		BarSeries series = createBarSeries();
		Strategy strategy2DaySma = create2DaySmaStrategy(series);
		Strategy strategy3DaySma = create6DaySmaStrategy(series);
		List<Strategy> strategies = new ArrayList<>();
		strategies.add(strategy2DaySma);
		strategies.add(strategy3DaySma);
		BarSeriesManager seriesManager = new BarSeriesManager(series);

		AnalysisCriterion profit = getCriterion();
		BacktestExecutor executor = new BacktestExecutor(profit);

		// the best strategy
		BacktestResult bestResult = executor.chooseBest(seriesManager, strategies);

		// the top 3 strategies
		List<BacktestResult> top3Results = executor.chooseBest(seriesManager, strategies, 3);

		// the best possible strategy with a required criterion value of 8
		BacktestResult bestResult_required_8 = executor.chooseBest(seriesManager, strategies, series.numOf(8));
		// the best possible strategy with a required criterion value of 9
		BacktestResult bestResult_required_9 = executor.chooseBest(seriesManager, strategies, series.numOf(9));
		// the best possible strategy with a required criterion value of 10
		BacktestResult bestResult_required_10 = executor.chooseBest(seriesManager, strategies, series.numOf(10));

		assertNumEquals(9, bestResult.getCalculatedNum());
		assertNumEquals(9, top3Results.get(0).getCalculatedNum());
		assertNumEquals(-10, top3Results.get(1).getCalculatedNum());
		assertTrue(bestResult_required_8.getAccepted());
		assertTrue(bestResult_required_9.getAccepted());
		assertFalse(bestResult_required_10.getAccepted());
	}

	private static BarSeries createBarSeries() {
		BarSeries series = new BaseBarSeries();
		series.addBar(createBar(CreateDay(1), 100.0, 100.0, 100.0, 100.0, 1060));
		series.addBar(createBar(CreateDay(2), 110.0, 110.0, 110.0, 110.0, 1070));
		series.addBar(createBar(CreateDay(3), 140.0, 140.0, 140.0, 140.0, 1080));
		series.addBar(createBar(CreateDay(4), 119.0, 119.0, 119.0, 119.0, 1090));
		series.addBar(createBar(CreateDay(5), 100.0, 100.0, 100.0, 100.0, 1100));
		series.addBar(createBar(CreateDay(6), 110.0, 110.0, 110.0, 110.0, 1110));
		series.addBar(createBar(CreateDay(7), 120.0, 120.0, 120.0, 120.0, 1120));
		series.addBar(createBar(CreateDay(8), 130.0, 130.0, 130.0, 130.0, 1130));
		return series;
	}

	private static BaseBar createBar(
			ZonedDateTime endTime,
			Number openPrice,
			Number highPrice,
			Number lowPrice,
			Number closePrice,
			Number volume) {
		return BaseBar.builder(DecimalNum::valueOf, Number.class)
				.timePeriod(Duration.ofDays(1))
				.endTime(endTime)
				.openPrice(openPrice)
				.highPrice(highPrice)
				.lowPrice(lowPrice)
				.closePrice(closePrice)
				.volume(volume)
				.build();
	}

	private static ZonedDateTime CreateDay(int day) {
		return ZonedDateTime.of(2018, 01, day, 12, 0, 0, 0, ZoneId.systemDefault());
	}

	private static Strategy create6DaySmaStrategy(BarSeries series) {
		OpenPriceIndicator openPrice = new OpenPriceIndicator(series);
		SMAIndicator sma = new SMAIndicator(openPrice, 6);
		return new BaseStrategy(new UnderIndicatorRule(sma, openPrice), new OverIndicatorRule(sma, openPrice));
	}

	private static Strategy create2DaySmaStrategy(BarSeries series) {
		ClosePriceIndicator closePrice = new ClosePriceIndicator(series);
		SMAIndicator sma = new SMAIndicator(closePrice, 2);
		return new BaseStrategy(new UnderIndicatorRule(sma, closePrice), new OverIndicatorRule(sma, closePrice));
	}

}
