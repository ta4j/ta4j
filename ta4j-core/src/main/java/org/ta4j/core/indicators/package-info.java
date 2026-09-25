/*
 * SPDX-License-Identifier: MIT
 */
/**
 * Technical indicators.
 *
 * <p>
 * Market indicators are mathematical transformations. They are used to assess
 * whether an asset is trending and, if so, the probability of its direction and
 * continuation.
 *
 * <p>
 * Kalman filtering of close prices is available through
 * {@link org.ta4j.core.indicators.KalmanFilterIndicator} for ordinary
 * Gaussian-scale noise and through
 * {@link org.ta4j.core.indicators.CorrentropyKalmanFilterIndicator} when
 * measurement outliers would otherwise pull the estimate; the companion
 * {@link org.ta4j.core.indicators.CorrentropyKalmanWeightIndicator} exposes the
 * per-measurement robustness weight in [0, 1]. The correntropy kernel bandwidth
 * is dimensionless because the fixed-point update whitens its errors, and Q/R
 * noise variances are in source units squared.
 *
 * @see <a href=
 *      "http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators">http://stockcharts.com/school/doku.php?id=chart_school:technical_indicators</a>
 */
package org.ta4j.core.indicators;