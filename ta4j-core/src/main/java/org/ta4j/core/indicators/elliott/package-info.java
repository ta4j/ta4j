/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Elliott Wave analysis indicators.
 *
 * <p>
 * Elliott Wave Theory, developed by Ralph Nelson Elliott, is a technical
 * analysis method that identifies recurring patterns in market price movements.
 * The theory posits that market prices move in predictable wave patterns that
 * reflect investor psychology and sentiment.
 *
 * <p>
 * This package provides a comprehensive suite of indicators for automated
 * Elliott Wave analysis, including:
 * <ul>
 * <li><b>Wave detection and counting</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottSwingIndicator} generates
 * Elliott swings from swing point data,
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveCountIndicator} counts
 * waves in the current pattern, and
 * {@link org.ta4j.core.indicators.elliott.ElliottSwingCompressor} filters and
 * compresses swings to identify wave structures.</li>
 * <li><b>Phase identification</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottPhaseIndicator} tracks the
 * current wave phase (impulse waves 1-5, corrective waves A-B-C), with
 * {@link org.ta4j.core.indicators.elliott.ElliottPhase} representing wave
 * phases with degree information.</li>
 * <li><b>Fibonacci analysis</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottRatioIndicator} calculates
 * Fibonacci retracement/extension ratios between waves,
 * {@link org.ta4j.core.indicators.elliott.ElliottFibonacciValidator} provides
 * continuous proximity scoring, and
 * {@link org.ta4j.core.indicators.elliott.ElliottConfluenceIndicator}
 * identifies price levels where multiple Fibonacci ratios converge.</li>
 * <li><b>Channel projection</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottChannelIndicator} projects
 * parallel trend channels for wave validation, with
 * {@link org.ta4j.core.indicators.elliott.ElliottChannel} representing the
 * channel boundaries.</li>
 * <li><b>Scenario-based analysis</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottScenarioIndicator} generates
 * multiple plausible wave interpretations with confidence scores.
 * {@link org.ta4j.core.indicators.elliott.ElliottConfidenceScorer} calculates
 * confidence from weighted factors, and
 * {@link org.ta4j.core.indicators.elliott.ElliottScenarioSet} holds ranked
 * alternative scenarios.</li>
 * <li><b>Invalidation and projections</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottInvalidationIndicator} flags
 * when wave counts break canonical invalidation levels,
 * {@link org.ta4j.core.indicators.elliott.ElliottInvalidationLevelIndicator}
 * returns specific price levels, and
 * {@link org.ta4j.core.indicators.elliott.ElliottProjectionIndicator} provides
 * Fibonacci-based price targets.</li>
 * <li><b>Facade API</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveFacade} provides a
 * high-level API that coordinates all indicators with consistent
 * configuration.</li>
 * </ul>
 *
 * <p>
 * <b>Getting Started</b>:
 * <p>
 * The simplest way to use Elliott Wave analysis is through the
 * {@link org.ta4j.core.indicators.elliott.ElliottWaveFacade}:
 *
 * <pre>
 * // Create facade using fractal-based swing detection
 * ElliottWaveFacade facade = ElliottWaveFacade.fractal(series, 5, ElliottDegree.INTERMEDIATE);
 *
 * // Get current wave phase
 * ElliottPhase phase = facade.phase().getValue(index);
 *
 * // Check Fibonacci ratios
 * ElliottRatio ratio = facade.ratio().getValue(index);
 *
 * // Get scenario-based analysis with confidence scores
 * ElliottScenarioSet scenarios = facade.scenarios().getValue(index);
 * </pre>
 *
 * <p>
 * All indicators work with swing point data from
 * {@link org.ta4j.core.indicators.RecentSwingIndicator} implementations
 * (fractal or ZigZag). The system treats {@link org.ta4j.core.num.DoubleNum}
 * NaN values as invalid using {@link org.ta4j.core.num.Num#isNaNOrNull(Num)}
 * and {@link org.ta4j.core.num.Num#isValid(Num)}, preventing NaN pivots,
 * ratios, and targets from leaking into analysis.
 *
 * <p>
 * <b>Key Concepts</b>:
 * <ul>
 * <li><b>Wave degrees</b>:
 * {@link org.ta4j.core.indicators.elliott.ElliottDegree} represents wave
 * degrees (SUBMINUETTE through GRAND_SUPERCYCLE) with methods for higher/lower
 * degree navigation.</li>
 * <li><b>Scenario types</b>:
 * {@link org.ta4j.core.indicators.elliott.ScenarioType} classifies pattern
 * types (IMPULSE, CORRECTIVE_ZIGZAG, CORRECTIVE_FLAT, CORRECTIVE_TRIANGLE,
 * etc.).</li>
 * <li><b>Confidence scoring</b>: Confidence is calculated from five weighted
 * factors: Fibonacci proximity (35%), time proportions (20%), alternation
 * quality (15%), channel adherence (15%), and structure completeness
 * (15%).</li>
 * </ul>
 *
 * @see <a href=
 *      "https://www.investopedia.com/terms/e/elliottwavetheory.asp">Investopedia:
 *      Elliott Wave Theory</a>
 * @see org.ta4j.core.indicators.elliott.ElliottWaveFacade
 * @see org.ta4j.core.indicators.RecentSwingIndicator
 * @since 0.22.0
 */
package org.ta4j.core.indicators.elliott;
