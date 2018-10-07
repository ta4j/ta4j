/**
 * {@link org.ta4j.core.num.Num Num} interface and implementations of {@link org.ta4j.core.num.NaN NaN}, 
 * {@link org.ta4j.core.num.DoubleNum DoubleNum}
 * and {@link org.ta4j.core.num.PrecisionNum PrecisionNum}
 * <p>
 * The {@link org.ta4j.core.num.Num Num interface} enables the use of different delegates (Double, {@link java.math.BigDecimal BigDecimal}, ...) for storage and calculations in {@link org.ta4j.core.TimeSeries TimeSeries},
 * {@link org.ta4j.core.Bar Bars}, {@link org.ta4j.core.Indicator Indicators} and {@link org.ta4j.core.analysis.criteria.AbstractAnalysisCriterion AnalysisCriterions}
 */
package org.ta4j.core.num;