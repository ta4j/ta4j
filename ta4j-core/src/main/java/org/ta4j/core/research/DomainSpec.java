/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.RandomAccess;

/**
 * Internal engine-facing view of one {@link ParameterDomain}: an O(1) indexed
 * canonical-value list plus optional continuous projection bounds for numeric
 * dimensions.
 *
 * <p>
 * Canonical values are generated with the same rules for every engine, so grid
 * iteration, genetic mutation, and swarm projection produce byte-identical
 * canonical strings for the same logical point.
 * </p>
 */
final class DomainSpec {

    /**
     * Upper bound on the number of declared positions that are eagerly enumerated
     * when a decimal domain's step is below the ULP of the largest magnitude in the
     * range: beyond this the declared domain is rejected instead of materializing a
     * huge distinct-value list.
     */
    private static final int COLLAPSE_VERIFICATION_LIMIT = 100_000;

    private final String name;
    private final List<String> values;
    private final int cardinality;
    private final boolean numeric;
    private final double lowerBound;
    private final double upperBound;
    private final double step;

    /**
     * Strictly increasing distinct doubles for a consolidated decimal domain,
     * aligned with {@link #values}; {@code null} for every other domain.
     */
    private final double[] distinctGrid;

    private DomainSpec(String name, List<String> values, int cardinality, boolean numeric, double lowerBound,
            double upperBound, double step, double[] distinctGrid) {
        this.name = name;
        this.values = values;
        this.cardinality = cardinality;
        this.numeric = numeric;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.step = step;
        this.distinctGrid = distinctGrid;
    }

    static DomainSpec of(ParameterResearch.ParameterDomain domain) {
        if (domain instanceof ParameterResearch.ParameterDomain.IntegerDomain d) {
            long count = ((long) d.to() - d.from()) / d.step() + 1L;
            int cardinality = checkedCardinality(d.name(), count);
            List<String> values = new IndexedValues(cardinality, index -> {
                return String.valueOf(d.from() + (long) index * d.step());
            });
            return new DomainSpec(d.name(), values, cardinality, true, d.from(), d.to(), d.step(), null);
        }
        if (domain instanceof ParameterResearch.ParameterDomain.DecimalDomain d) {
            // Exact decimal-string arithmetic: double division misrounds the
            // ratio for non-dyadic bounds and can drop or add a declared end
            // position. BigDecimal.valueOf parses via the canonical decimal
            // string of the double, so span and step are the exact declared
            // decimals; the quotient is a dyadic rational, and its exact value
            // is at least 2^-107, so 50 decimal digits never misround it.
            BigDecimal span = BigDecimal.valueOf(d.to()).subtract(BigDecimal.valueOf(d.from()));
            BigDecimal ratio = span.divide(BigDecimal.valueOf(d.step()), new MathContext(50, RoundingMode.FLOOR));
            // Floor first: a non-integral ratio may exceed the limit while its
            // floored position count is still legal (e.g. 0..2147483646.5 step
            // 1 declares exactly Integer.MAX_VALUE values).
            BigDecimal floored = ratio.setScale(0, RoundingMode.FLOOR);
            if (floored.compareTo(BigDecimal.valueOf(Integer.MAX_VALUE - 1L)) > 0) {
                throw new IllegalArgumentException("Domain '" + d.name() + "' declares more than " + Integer.MAX_VALUE
                        + " values which exceeds the per-domain limit of " + Integer.MAX_VALUE);
            }
            long count = floored.longValueExact() + 1L;
            int cardinality = checkedCardinality(d.name(), count);
            // Whenever the step is below the ULP at the range's largest
            // magnitude, consecutive declared positions can collapse to the
            // same double (steps between half and one ULP can still merge
            // pairs of positions). Enumerate the distinct canonical values
            // eagerly so the reported cardinality matches the values that can
            // actually be evaluated, keeping search-space exhaustion honest.
            if (d.step() < Math.ulp(Math.max(Math.abs(d.from()), Math.abs(d.to())))) {
                if (cardinality > COLLAPSE_VERIFICATION_LIMIT) {
                    throw new IllegalArgumentException("Domain '" + d.name() + "' declares " + cardinality
                            + " values at a step below ULP precision; double arithmetic cannot represent "
                            + "the declared positions distinctly");
                }
                List<String> distinct = new ArrayList<>(cardinality);
                // The doubles produced below are monotonically non-decreasing
                // and duplicates collapse together with their canonical
                // strings, so the retained grid is strictly increasing and
                // aligned with `distinct`.
                double[] grid = new double[cardinality];
                int gridSize = 0;
                String previous = null;
                for (int index = 0; index < cardinality; index++) {
                    double value = BigDecimal.valueOf(d.from())
                            .add(BigDecimal.valueOf(d.step()).multiply(BigDecimal.valueOf(index)))
                            .doubleValue();
                    String canonical = ParameterResearch.canonicalDecimal(Math.min(value, d.to()));
                    if (!canonical.equals(previous)) {
                        distinct.add(canonical);
                        grid[gridSize++] = value;
                        previous = canonical;
                    }
                }
                return new DomainSpec(d.name(), distinct, distinct.size(), true, d.from(), d.to(), d.step(),
                        Arrays.copyOf(grid, gridSize));
            }
            List<String> values = new IndexedValues(cardinality, index -> {
                double value = BigDecimal.valueOf(d.from())
                        .add(BigDecimal.valueOf(d.step()).multiply(BigDecimal.valueOf(index)))
                        .doubleValue();
                return ParameterResearch.canonicalDecimal(Math.min(value, d.to()));
            });
            return new DomainSpec(d.name(), values, cardinality, true, d.from(), d.to(), d.step(), null);
        }
        if (domain instanceof ParameterResearch.ParameterDomain.BooleanDomain d) {
            return new DomainSpec(d.name(), List.of("false", "true"), 2, false, Double.NaN, Double.NaN, Double.NaN,
                    null);
        }
        if (domain instanceof ParameterResearch.ParameterDomain.CategoricalDomain d) {
            return new DomainSpec(d.name(), d.values(), d.values().size(), false, Double.NaN, Double.NaN, Double.NaN,
                    null);
        }
        throw new IllegalArgumentException("Unsupported parameter domain: " + domain.getClass().getName());
    }

    private static int checkedCardinality(String name, long count) {
        if (count > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Domain '" + name + "' has " + count
                    + " values which exceeds the per-domain limit of " + Integer.MAX_VALUE);
        }
        return (int) count;
    }

    /**
     * @return parameter name
     */
    String name() {
        return name;
    }

    /**
     * @return O(1) indexed canonical value list
     */
    List<String> values() {
        return values;
    }

    /**
     * @return number of canonical values
     */
    int cardinality() {
        return cardinality;
    }

    /**
     * @param index value index
     * @return canonical value at the index
     */
    String valueAt(int index) {
        return values.get(index);
    }

    /**
     * @return whether this dimension is an ordered integer or decimal domain
     */
    boolean numeric() {
        return numeric;
    }

    /**
     * @return continuous lower bound, or {@code NaN} when not numeric
     */
    double lowerBound() {
        return lowerBound;
    }

    /**
     * @return continuous upper bound, or {@code NaN} when not numeric
     */
    double upperBound() {
        return upperBound;
    }

    /**
     * Returns the continuous grid point for a value index: the consolidated
     * distinct double when the domain collapses below-ULP positions, otherwise
     * {@code lowerBound + index * step}.
     *
     * @param index value index in {@code [0, cardinality)}
     * @return continuous position of the grid point
     */
    double gridPointAt(int index) {
        return distinctGrid != null ? distinctGrid[index] : lowerBound + index * step;
    }

    /**
     * @return continuous grid step, or {@code NaN} when not numeric
     */
    double step() {
        return step;
    }

    /**
     * Projects a continuous position onto the discrete grid: the index of the
     * nearest grid point, clamped to the domain. For decimal domains whose declared
     * positions consolidate into distinct doubles, the projection follows the
     * consolidated distinct values instead of the declared step grid, so swarm
     * coordinates map to the values that are actually evaluated.
     *
     * @param position continuous position
     * @return grid index in {@code [0, cardinality)}
     */
    int projectIndex(double position) {
        if (distinctGrid != null) {
            int index = Arrays.binarySearch(distinctGrid, position);
            if (index >= 0) {
                return index;
            }
            index = -index - 1;
            if (index <= 0) {
                return 0;
            }
            if (index >= distinctGrid.length) {
                return distinctGrid.length - 1;
            }
            return position - distinctGrid[index - 1] <= distinctGrid[index] - position ? index - 1 : index;
        }
        int index = (int) Math.round((position - lowerBound) / step);
        if (index < 0) {
            return 0;
        }
        if (index >= cardinality) {
            return cardinality - 1;
        }
        return index;
    }

    /**
     * O(1) random-access view over computed canonical values.
     */
    private static final class IndexedValues extends AbstractList<String> implements RandomAccess {

        private final int size;
        private final java.util.function.IntFunction<String> generator;

        private IndexedValues(int size, java.util.function.IntFunction<String> generator) {
            this.size = size;
            this.generator = generator;
        }

        @Override
        public String get(int index) {
            if (index < 0 || index >= size) {
                throw new IndexOutOfBoundsException("index out of range: " + index);
            }
            return generator.apply(index);
        }

        @Override
        public int size() {
            return size;
        }
    }
}
