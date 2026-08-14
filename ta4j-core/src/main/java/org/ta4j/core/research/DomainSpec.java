/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.research;

import java.math.BigDecimal;
import java.util.AbstractList;
import java.util.ArrayList;
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
     * when a decimal domain's step is at or below half-ULP precision: beyond this
     * the declared domain is rejected instead of materializing a huge
     * distinct-value list.
     */
    private static final int COLLAPSE_VERIFICATION_LIMIT = 100_000;

    private final String name;
    private final List<String> values;
    private final int cardinality;
    private final boolean numeric;
    private final double lowerBound;
    private final double upperBound;
    private final double step;

    private DomainSpec(String name, List<String> values, int cardinality, boolean numeric, double lowerBound,
            double upperBound, double step) {
        this.name = name;
        this.values = values;
        this.cardinality = cardinality;
        this.numeric = numeric;
        this.lowerBound = lowerBound;
        this.upperBound = upperBound;
        this.step = step;
    }

    static DomainSpec of(ParameterResearch.ParameterDomain domain) {
        if (domain instanceof ParameterResearch.ParameterDomain.IntegerDomain d) {
            long count = ((long) d.to() - d.from()) / d.step() + 1L;
            int cardinality = checkedCardinality(d.name(), count);
            List<String> values = new IndexedValues(cardinality, index -> {
                return String.valueOf(d.from() + (long) index * d.step());
            });
            return new DomainSpec(d.name(), values, cardinality, true, d.from(), d.to(), d.step());
        }
        if (domain instanceof ParameterResearch.ParameterDomain.DecimalDomain d) {
            double ratio = Math.floor((d.to() - d.from()) / d.step() + 1e-9);
            if (ratio > Integer.MAX_VALUE - 1d) {
                throw new IllegalArgumentException("Domain '" + d.name() + "' declares more than " + Integer.MAX_VALUE
                        + " values which exceeds the per-domain limit of " + Integer.MAX_VALUE);
            }
            long count = (long) ratio + 1L;
            int cardinality = checkedCardinality(d.name(), count);
            // When the step is at or below half the ULP of the largest magnitude
            // in the range, consecutive declared positions collapse to the same
            // double. Enumerate the distinct canonical values eagerly so the
            // reported cardinality matches the values that can actually be
            // evaluated, keeping search-space exhaustion honest.
            if (d.step() <= Math.ulp(Math.max(Math.abs(d.from()), Math.abs(d.to()))) / 2d) {
                if (cardinality > COLLAPSE_VERIFICATION_LIMIT) {
                    throw new IllegalArgumentException("Domain '" + d.name() + "' declares " + cardinality
                            + " values at a step below half-ULP precision; double arithmetic cannot represent "
                            + "the declared positions distinctly");
                }
                List<String> distinct = new ArrayList<>(cardinality);
                String previous = null;
                for (int index = 0; index < cardinality; index++) {
                    double value = BigDecimal.valueOf(d.from())
                            .add(BigDecimal.valueOf(d.step()).multiply(BigDecimal.valueOf(index)))
                            .doubleValue();
                    String canonical = ParameterResearch.canonicalDecimal(Math.min(value, d.to()));
                    if (!canonical.equals(previous)) {
                        distinct.add(canonical);
                        previous = canonical;
                    }
                }
                return new DomainSpec(d.name(), distinct, distinct.size(), true, d.from(), d.to(), d.step());
            }
            List<String> values = new IndexedValues(cardinality, index -> {
                double value = BigDecimal.valueOf(d.from())
                        .add(BigDecimal.valueOf(d.step()).multiply(BigDecimal.valueOf(index)))
                        .doubleValue();
                return ParameterResearch.canonicalDecimal(Math.min(value, d.to()));
            });
            return new DomainSpec(d.name(), values, cardinality, true, d.from(), d.to(), d.step());
        }
        if (domain instanceof ParameterResearch.ParameterDomain.BooleanDomain d) {
            return new DomainSpec(d.name(), List.of("false", "true"), 2, false, Double.NaN, Double.NaN, Double.NaN);
        }
        if (domain instanceof ParameterResearch.ParameterDomain.CategoricalDomain d) {
            return new DomainSpec(d.name(), d.values(), d.values().size(), false, Double.NaN, Double.NaN, Double.NaN);
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
     * @return continuous grid step, or {@code NaN} when not numeric
     */
    double step() {
        return step;
    }

    /**
     * Projects a continuous position onto the discrete grid: the index of the
     * nearest grid point, clamped to the domain.
     *
     * @param position continuous position
     * @return grid index in {@code [0, cardinality)}
     */
    int projectIndex(double position) {
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
     * Continuous position of a grid index.
     *
     * @param index grid index
     * @return continuous position
     */
    double positionOf(int index) {
        return lowerBound + index * step;
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
