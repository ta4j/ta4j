/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.criteria.pnl;

import org.ta4j.core.BarSeries;
import org.ta4j.core.Position;
import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Gross return criterion.
 *
 * <p>
 * Calculates the gross return of positions, where trading costs are not
 * deducted from the calculation. The output format is controlled by the
 * {@link ReturnRepresentation} specified in the constructor or the global
 * default from {@link org.ta4j.core.criteria.ReturnRepresentationPolicy}.
 *
 * <p>
 * Examples for a +12% return:
 * <ul>
 * <li>MULTIPLICATIVE: 1.12 (includes base, growth factor)
 * <li>DECIMAL: 0.12 (excludes base, decimal fraction)
 * <li>PERCENTAGE: 12.0 (percentage value)
 * <li>LOG: ln(1.12) ≈ 0.113 (logarithmic return)
 * </ul>
 *
 * <p>
 * The return of the provided {@link Position position(s)} over the provided
 * {@link BarSeries series}.
 *
 * @see ReturnRepresentation
 * @see org.ta4j.core.criteria.ReturnRepresentationPolicy
 */
public class GrossReturnCriterion extends AbstractReturnCriterion {

    public GrossReturnCriterion() {
        super();
    }

    public GrossReturnCriterion(ReturnRepresentation representation) {
        super(representation);
    }

    @Deprecated(since = "0.24.0")
    public GrossReturnCriterion(boolean addBase) {
        super(addBase);
    }

    @Override
    protected Num calculateReturn(BarSeries series, Position position) {
        return position.getGrossReturn(series);
    }

}
