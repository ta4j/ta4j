/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators;

import java.io.Serial;
import java.lang.reflect.Array;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.temporal.TemporalAccessor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.ta4j.core.Bar;
import org.ta4j.core.BarBuilder;
import org.ta4j.core.BarSeries;
import org.ta4j.core.Indicator;
import org.ta4j.core.num.Num;
import org.ta4j.core.num.NumFactory;
import org.ta4j.core.indicators.numeric.NumericIndicator;

/**
 * Abstract {@link Indicator indicator}.
 *
 * @param <T> result type
 */
public abstract class AbstractIndicator<T> implements Indicator<T> {

    /** The logger. */
    protected final Logger log = LoggerFactory.getLogger(getClass());

    private final BarSeries series;
    private final IndicatorIdentity identity;

    /**
     * Constructor.
     *
     * @param series the bar series
     */
    protected AbstractIndicator(BarSeries series) {
        this.series = unwrapBarSeries(series);
        this.identity = null;
    }

    /**
     * Creates an indicator that can share deterministic calculation state with
     * structurally equivalent indicators on the same series.
     *
     * @param series   the bar series
     * @param identity the immutable constructor inputs returned by
     *                 {@link #identityOf(Object...)}
     * @since 0.23.1
     */
    protected AbstractIndicator(BarSeries series, IndicatorIdentity identity) {
        this.series = unwrapBarSeries(series);
        this.identity = identity == null ? null : identity.forClass(getClass());
    }

    /**
     * Captures immutable constructor inputs for transparent series-scoped sharing.
     * Unsupported inputs use object identity, favoring isolation over unsafe
     * equality.
     *
     * @param parts the inputs that completely determine indicator output
     * @return an identity descriptor for a superclass constructor
     * @since 0.23.1
     */
    protected static IndicatorIdentity identityOf(Object... parts) {
        return identityOfExact(null, parts);
    }

    /**
     * Limits an audited built-in identity to its exact concrete class so subclasses
     * remain isolated until they declare their complete identity themselves.
     *
     * @param exactClass the audited concrete class
     * @param parts      immutable constructor inputs
     * @return an exact-class identity descriptor
     * @since 0.23.1
     */
    protected static IndicatorIdentity identityOfExact(Class<?> exactClass, Object... parts) {
        List<Object> normalized = new ArrayList<>(parts.length);
        for (Object part : parts) {
            normalized.add(normalizeIdentityPart(part));
        }
        return new IndicatorIdentity(exactClass, null, Collections.unmodifiableList(normalized));
    }

    @Override
    public BarSeries getBarSeries() {
        return new ReadOnlyBarSeriesView(series);
    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }

    static BarSeries unwrapBarSeries(BarSeries barSeries) {
        BarSeries currentSeries = Objects.requireNonNull(barSeries, "barSeries");
        while (currentSeries instanceof ReadOnlyBarSeriesView view) {
            currentSeries = view.delegate;
        }
        return currentSeries;
    }

    final BarSeries underlyingBarSeries() {
        return series;
    }

    final IndicatorIdentity indicatorIdentity() {
        return identity;
    }

    private static Object normalizeIdentityPart(Object part) {
        if (part == null || part instanceof String || part instanceof Boolean || part instanceof Character
                || part instanceof Byte || part instanceof Short || part instanceof Integer || part instanceof Long
                || part instanceof Float || part instanceof Double || part instanceof BigInteger
                || part instanceof BigDecimal || part instanceof Enum<?> || part instanceof Class<?>
                || part instanceof TemporalAccessor || part instanceof Num) {
            return part;
        }
        if (part instanceof AbstractIndicator<?> indicator) {
            IndicatorIdentity sourceIdentity = indicator.indicatorIdentity();
            return sourceIdentity == null ? new OpaqueIdentity(part) : sourceIdentity;
        }
        if (part instanceof NumericIndicator numericIndicator) {
            return normalizeIdentityPart(numericIndicator.delegate());
        }
        if (part.getClass().isArray()) {
            int length = Array.getLength(part);
            List<Object> normalized = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                normalized.add(normalizeIdentityPart(Array.get(part, i)));
            }
            return Collections.unmodifiableList(normalized);
        }
        if (part instanceof Collection<?> collection) {
            List<Object> normalized = new ArrayList<>(collection.size());
            for (Object element : collection) {
                normalized.add(normalizeIdentityPart(element));
            }
            return Collections.unmodifiableList(normalized);
        }
        if (part instanceof Map<?, ?> map) {
            Map<Object, Object> normalized = new java.util.HashMap<>();
            map.forEach((key, value) -> normalized.put(normalizeIdentityPart(key), normalizeIdentityPart(value)));
            return Collections.unmodifiableMap(normalized);
        }
        return new OpaqueIdentity(part);
    }

    /**
     * Structural identity for an opted-in deterministic indicator.
     *
     * @since 0.23.1
     */
    protected static final class IndicatorIdentity {

        private final Class<?> exactClass;
        private final Class<?> indicatorClass;
        private final List<Object> parts;

        private IndicatorIdentity(Class<?> exactClass, Class<?> indicatorClass, List<Object> parts) {
            this.exactClass = exactClass;
            this.indicatorClass = indicatorClass;
            this.parts = parts;
        }

        private IndicatorIdentity forClass(Class<?> type) {
            Class<?> actualType = Objects.requireNonNull(type, "type");
            return exactClass != null && exactClass != actualType ? null
                    : new IndicatorIdentity(exactClass, actualType, parts);
        }

        @Override
        public boolean equals(Object other) {
            return this == other || other instanceof IndicatorIdentity identity
                    && indicatorClass == identity.indicatorClass && parts.equals(identity.parts);
        }

        @Override
        public int hashCode() {
            return 31 * Objects.hashCode(indicatorClass) + parts.hashCode();
        }
    }

    private static final class OpaqueIdentity {

        private final Object value;

        private OpaqueIdentity(Object value) {
            this.value = value;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof OpaqueIdentity identity && value == identity.value;
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(value);
        }
    }

    private static final class ReadOnlyBarSeriesView implements BarSeries {

        @Serial
        private static final long serialVersionUID = 8231541227186054452L;

        private final BarSeries delegate;

        private ReadOnlyBarSeriesView(BarSeries delegate) {
            this.delegate = Objects.requireNonNull(delegate, "delegate");
        }

        @Override
        public NumFactory numFactory() {
            return delegate.numFactory();
        }

        @Override
        public BarBuilder barBuilder() {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public String getName() {
            return delegate.getName();
        }

        @Override
        public Bar getBar(int i) {
            return delegate.getBar(i);
        }

        @Override
        public int getBarCount() {
            return delegate.getBarCount();
        }

        @Override
        public List<Bar> getBarData() {
            return List.copyOf(delegate.getBarData());
        }

        @Override
        public IndicatorContext indicators() {
            return delegate.indicators();
        }

        @Override
        public long getBarHistoryEpoch() {
            return delegate.getBarHistoryEpoch();
        }

        @Override
        public long getBarHistoryRevision() {
            return delegate.getBarHistoryRevision();
        }

        @Override
        public void clear() {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public int getBeginIndex() {
            return delegate.getBeginIndex();
        }

        @Override
        public int getEndIndex() {
            return delegate.getEndIndex();
        }

        @Override
        public int getMaximumBarCount() {
            return delegate.getMaximumBarCount();
        }

        @Override
        public void setMaximumBarCount(int maximumBarCount) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public int getRemovedBarsCount() {
            return delegate.getRemovedBarsCount();
        }

        @Override
        public void addBar(Bar bar, boolean replace) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public void addTrade(Num tradeVolume, Num tradePrice) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public void addPrice(Num price) {
            throw new UnsupportedOperationException("Indicator bar series views are read-only");
        }

        @Override
        public BarSeries getSubSeries(int startIndex, int endIndex) {
            return delegate.getSubSeries(startIndex, endIndex);
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof BarSeries otherSeries && unwrapBarSeries(delegate) == unwrapBarSeries(otherSeries);
        }

        @Override
        public int hashCode() {
            return System.identityHashCode(unwrapBarSeries(delegate));
        }

        @Override
        public String toString() {
            return delegate.toString();
        }
    }
}
