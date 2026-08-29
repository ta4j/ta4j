/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Candlestick indicators separate candle geometry, adaptive thresholds, named
 * morphology, and caller-owned market context.
 *
 * <p>
 * Build a rule in four layers:
 * </p>
 * <ol>
 * <li><strong>Geometry</strong>: derive reusable measurements with
 * {@link org.ta4j.core.indicators.candles.CandleBodyIndicator},
 * {@link org.ta4j.core.indicators.candles.CandleRangeIndicator},
 * {@link org.ta4j.core.indicators.candles.UpperShadowIndicator}, and
 * {@link org.ta4j.core.indicators.candles.LowerShadowIndicator}.</li>
 * <li><strong>Adaptive thresholds</strong>: compare those measurements with
 * preceding-window averages.</li>
 * <li><strong>Named morphology</strong>: use a named pattern indicator when its
 * candle-shape contract matches the intended condition.</li>
 * <li><strong>Context</strong>: explicitly compose trend, confirmation, volume,
 * risk, and trading-record conditions in the caller's {@link org.ta4j.core.Rule
 * Rule}.</li>
 * </ol>
 *
 * <h2>Geometry</h2>
 * 
 * <pre>{@code
 * body = |close - open|
 * bodyTop = max(open, close)
 * bodyBottom = min(open, close)
 * upperShadow = high - bodyTop
 * lowerShadow = bodyBottom - low
 * range = high - low = upperShadow + body + lowerShadow
 * }</pre>
 *
 * <p>
 * Body extrema do not need new public feature types. Compose them directly when
 * needed:
 * </p>
 * 
 * <pre>{@code
 * Indicator<Num> bodyTop = BinaryOperationIndicator.max(new OpenPriceIndicator(series),
 *         new ClosePriceIndicator(series));
 * Indicator<Num> bodyBottom = BinaryOperationIndicator.min(new OpenPriceIndicator(series),
 *         new ClosePriceIndicator(series));
 * }</pre>
 *
 * <h2>Adaptive threshold endpoints</h2>
 * <p>
 * The shared threshold profile derives each decision from preceding bars only:
 * the current bar never contributes to its own threshold. Its default average
 * period is five bars, and
 * {@link org.ta4j.core.Indicator#getCountOfUnstableBars()
 * getCountOfUnstableBars()} exposes the causal warm-up boundary.
 * </p>
 * <table>
 * <caption>Shared morphology endpoints</caption>
 * <tr>
 * <th>Condition</th>
 * <th>Endpoint</th>
 * </tr>
 * <tr>
 * <td>Long body</td>
 * <td>{@code body > preceding-average body}</td>
 * </tr>
 * <tr>
 * <td>Short body</td>
 * <td>{@code body < 0.5 * preceding-average body}</td>
 * </tr>
 * <tr>
 * <td>Doji, short shadow, or near value</td>
 * <td>{@code measurement <= 0.1 * preceding-average range}</td>
 * </tr>
 * </table>
 *
 * <p>
 * Pattern-specific gaps and reversal crossings are strict. Containment and
 * penetration endpoints are inclusive, except the crows' containment:
 * {@link org.ta4j.core.indicators.candles.ThreeBlackCrowsIndicator
 * ThreeBlackCrowsIndicator} requires each current open to be strictly inside
 * the previous body, rejecting opens exactly on either boundary.
 * </p>
 *
 * <h2>Compose morphology with explicit context</h2>
 * <p>
 * The replacement pattern indicators describe morphology only. They are not
 * trading signals or conventional forecast assertions, and they do not hide
 * ADX, trend, or other market context; compose those conditions explicitly.
 * Exception: the deprecated compatibility indicators
 * {@link org.ta4j.core.indicators.candles.DarkCloudIndicator
 * DarkCloudIndicator} and
 * {@link org.ta4j.core.indicators.candles.PiercingIndicator PiercingIndicator}
 * still gate their results with {@code UpTrendIndicator} and
 * {@code DownTrendIndicator} respectively, so do not add a duplicate trend
 * filter when using them. For a two-candle
 * {@link org.ta4j.core.indicators.candles.PiercingLineIndicator piercing line},
 * shift context by two bars so it ends before the pattern window:
 * </p>
 * 
 * <pre>{@code
 * Indicator<Num> close = new ClosePriceIndicator(series);
 * Indicator<Num> closeBeforePattern = new PreviousValueIndicator(close, 2);
 * Indicator<Num> averageBeforePattern = new PreviousValueIndicator(new SMAIndicator(close, 20), 2);
 * Rule priorDowntrend = new UnderIndicatorRule(closeBeforePattern, averageBeforePattern);
 * Rule reversalCandidate = new BooleanIndicatorRule(new PiercingLineIndicator(series)).and(priorDowntrend);
 * }</pre>
 *
 * <p>
 * The two-bar shift means both context values end at the bar immediately before
 * the two-candle pattern begins.
 * </p>
 *
 * <h2>Raw custom morphology</h2>
 * <p>
 * The following intentionally custom strict profile is not a canonical named
 * pattern. It demonstrates direct composition without introducing a redundant
 * public indicator:
 * </p>
 * 
 * <pre>{@code
 * Indicator<Num> body = new CandleBodyIndicator(series);
 * Indicator<Num> range = new CandleRangeIndicator(series);
 * Indicator<Num> upperShadow = new UpperShadowIndicator(series);
 * Indicator<Num> lowerShadow = new LowerShadowIndicator(series);
 * Indicator<Num> halfRange = BinaryOperationIndicator.product(range, 0.5);
 * Indicator<Num> tenthRange = BinaryOperationIndicator.product(range, 0.1);
 * Rule customMorphology = new OverIndicatorRule(body, halfRange).and(new UnderIndicatorRule(upperShadow, tenthRange))
 *         .and(new UnderIndicatorRule(lowerShadow, tenthRange));
 * }</pre>
 */
package org.ta4j.core.indicators.candles;
