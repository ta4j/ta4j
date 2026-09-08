/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core;

import java.io.IOException;
import java.io.InvalidObjectException;
import java.io.ObjectInputStream;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Objects;
import java.util.TreeMap;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.ta4j.core.num.Num;

/**
 * Base implementation of a {@link Bar}.
 */
public class BaseBar implements Bar {
    private static final long serialVersionUID = 8038383777467488147L;

    private static final ThreadLocal<MutationState> MUTATION_STATE = new ThreadLocal<>();

    /**
     * Weak retaining-series registrations. The weak keys avoid retaining
     * short-lived subseries through bars that they shallow-copy.
     */
    private transient Map<IdentityKey<BaseBarSeries>, RetainingSeriesRegistration> retainingSeries = new HashMap<>();
    private transient ReferenceQueue<BaseBarSeries> retainingSeriesQueue = new ReferenceQueue<>();

    private record RetainedSeriesMutation(BaseBarSeries series, int index) {
    }

    static final class RetainedBarMutationPublication {

        private final BaseBar bar;
        private final List<RetainedSeriesMutation> mutations;
        private final int publicationCount;
        private final Throwable failure;
        private final List<RetainedBarMutationPublication> nestedPublications;

        private RetainedBarMutationPublication(final BaseBar bar, final List<RetainedSeriesMutation> mutations,
                final int publicationCount, final Throwable failure) {
            this(bar, mutations, publicationCount, failure, List.of());
        }

        private RetainedBarMutationPublication(final BaseBar bar, final List<RetainedSeriesMutation> mutations,
                final int publicationCount, final Throwable failure,
                final List<RetainedBarMutationPublication> nestedPublications) {
            this.bar = bar;
            this.mutations = mutations;
            this.publicationCount = publicationCount;
            this.failure = failure;
            this.nestedPublications = List.copyOf(nestedPublications);
        }

        private static RetainedBarMutationPublication combine(final RetainedBarMutationPublication primary,
                final List<RetainedBarMutationPublication> nestedPublications) {
            if (nestedPublications.isEmpty()) {
                return primary;
            }
            if (primary == null) {
                return new RetainedBarMutationPublication(null, List.of(), 0, null, nestedPublications);
            }
            return new RetainedBarMutationPublication(primary.bar, primary.mutations, primary.publicationCount,
                    primary.failure, nestedPublications);
        }

        void publish() {
            Throwable publicationFailure = mergeFailures(this.failure, publishCallbacks());
            for (RetainedBarMutationPublication nestedPublication : nestedPublications) {
                try {
                    nestedPublication.publish();
                } catch (RuntimeException | Error cause) {
                    publicationFailure = mergeFailures(publicationFailure, cause);
                }
            }
            if (publicationFailure instanceof RuntimeException exception) {
                throw exception;
            }
            if (publicationFailure instanceof Error error) {
                throw error;
            }
        }

        private Throwable publishCallbacks() {
            Throwable publicationFailure = null;
            for (int publication = 0; publication < publicationCount; publication++) {
                for (RetainedSeriesMutation mutation : mutations) {
                    try {
                        mutation.series().retainedBarMutated(bar, mutation.index());
                    } catch (RuntimeException | Error cause) {
                        publicationFailure = mergeFailures(publicationFailure, cause);
                    }
                }
            }
            return publicationFailure;
        }

        private static Throwable mergeFailures(final Throwable first, final Throwable second) {
            if (first == null) {
                return second;
            }
            if (second == null) {
                return first;
            }
            if (first != second) {
                first.addSuppressed(second);
            }
            return first;
        }
    }

    private static final class MutationState {

        private final BaseBar bar;
        private final MutationState nestedPublicationSink;
        private List<RetainedBarMutationPublication> nestedPublications;
        private int suppressionDepth;
        private int publicationCount;

        private MutationState(final BaseBar bar, final boolean defersNestedPublication) {
            this.bar = bar;
            this.nestedPublicationSink = defersNestedPublication ? this : null;
        }

        private MutationState(final BaseBar bar, final MutationState enclosingState) {
            this.bar = bar;
            this.nestedPublicationSink = enclosingState == null ? null : enclosingState.nestedPublicationSink;
        }

        private boolean defersNestedPublication() {
            return nestedPublicationSink != null;
        }

        private void deferNestedPublication(final RetainedBarMutationPublication publication) {
            if (nestedPublicationSink != this) {
                nestedPublicationSink.deferNestedPublication(publication);
                return;
            }
            if (nestedPublications == null) {
                nestedPublications = new ArrayList<>();
            }
            nestedPublications.add(publication);
        }

        private List<RetainedBarMutationPublication> nestedPublications() {
            return nestedPublications == null ? List.of() : nestedPublications;
        }
    }

    /**
     * A weak identity key keeps equal but distinct series registrations separate.
     */
    private static final class IdentityKey<K> extends WeakReference<K> {

        private final int identityHash;

        private IdentityKey(final K referent, final ReferenceQueue<K> queue) {
            super(referent, queue);
            this.identityHash = System.identityHashCode(referent);
        }

        @Override
        public int hashCode() {
            return identityHash;
        }

        @Override
        public boolean equals(final Object other) {
            if (other == this) {
                return true;
            }
            if (!(other instanceof IdentityKey<?>)) {
                return false;
            }
            final K referent = get();
            return referent != null && referent == ((IdentityKey<?>) other).get();
        }
    }

    private static final class RetainingSeriesRegistration {

        private int firstIndex;
        private NavigableMap<Integer, Boolean> additionalIndexes;

        private RetainingSeriesRegistration(final int index) {
            this.firstIndex = index;
        }

        private void attach(final int index) {
            if (index < firstIndex) {
                indexes().put(firstIndex, Boolean.TRUE);
                firstIndex = index;
            } else if (index > firstIndex) {
                indexes().put(index, Boolean.TRUE);
            }
        }

        private boolean detach(final int index) {
            if (index != firstIndex) {
                if (additionalIndexes != null) {
                    additionalIndexes.remove(index);
                }
                return true;
            }
            if (additionalIndexes == null || additionalIndexes.isEmpty()) {
                return false;
            }
            firstIndex = additionalIndexes.firstKey();
            additionalIndexes.pollFirstEntry();
            return true;
        }

        private int firstIndex() {
            return firstIndex;
        }

        private NavigableMap<Integer, Boolean> indexes() {
            if (additionalIndexes == null) {
                additionalIndexes = new TreeMap<>();
            }
            return additionalIndexes;
        }
    }

    /** The time period (e.g. 1 day, 15 min, etc.) of the bar. */
    private final Duration timePeriod;

    /** The begin time of the bar period (in UTC). */
    private final Instant beginTime;

    /** The end time of the bar period (in UTC). */
    private final Instant endTime;

    /** The open price of the bar period. */
    private Num openPrice;

    /** The high price of the bar period. */
    private Num highPrice;

    /** The low price of the bar period. */
    private Num lowPrice;

    /** The close price of the bar period. */
    private Num closePrice;

    /** The total traded volume of the bar period. */
    private Num volume;

    /** The total traded amount of the bar period. */
    private Num amount;

    /** The number of trades of the bar period. */
    private long trades;

    /**
     * Constructor.
     *
     * <ul>
     * <li>If {@link #timePeriod} is not provided, it will be calculated as
     * {@link #endTime} - {@link #beginTime}.
     * <li>If {@link #beginTime} is not provided, it will be calculated as
     * {@link #endTime} - {@link #timePeriod}.
     * <li>If {@link #endTime} is not provided, it will be calculated as
     * {@link #beginTime} + {@link #timePeriod}.
     * </ul>
     *
     * @param timePeriod the time period (optional if beginTime and endTime is
     *                   given)
     * @param beginTime  the begin time of the bar period (in UTC) (optional if
     *                   endTime is given)
     * @param endTime    the end time of the bar period (in UTC) (optional if
     *                   beginTime is given)
     * @param openPrice  the open price of the bar period
     * @param highPrice  the highest price of the bar period
     * @param lowPrice   the lowest price of the bar period
     * @param closePrice the close price of the bar period
     * @param volume     the total traded volume of the bar period
     * @param amount     the total traded amount of the bar period
     * @param trades     the number of trades of the bar period
     * @throws NullPointerException     if given or calculated {@link #timePeriod},
     *                                  {@link #beginTime} or {@link #endTime}
     *                                  values are {@code null}
     * @throws IllegalArgumentException if the calculated timePeriod between the
     *                                  provided beginTime and endTime does not
     *                                  match the provided timePeriod, if the high
     *                                  price is below the low price, the open
     *                                  price, or the close price, if the low price
     *                                  is above the open price or the close price,
     *                                  if volume or amount is negative, or if the
     *                                  number of trades is negative
     */
    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Fail-fast validation of bar data is a documented constructor contract: invalid bars "
            + "are rejected before any partially initialized instance can escape")
    public BaseBar(Duration timePeriod, Instant beginTime, Instant endTime, Num openPrice, Num highPrice, Num lowPrice,
            Num closePrice, Num volume, Num amount, long trades) {

        this(resolvedTimes(timePeriod, beginTime, endTime), openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @SuppressFBWarnings(value = "CT_CONSTRUCTOR_THROW", justification = "Fail-fast validation of bar data is a documented constructor contract: invalid bars "
            + "are rejected before any partially initialized instance can escape")
    private BaseBar(ResolvedTimes times, Num openPrice, Num highPrice, Num lowPrice, Num closePrice, Num volume,
            Num amount, long trades) {
        validatePrices(openPrice, highPrice, lowPrice, closePrice);
        if (volume != null && volume.isNegative()) {
            throw new IllegalArgumentException("Volume cannot be negative, but was " + volume);
        }
        if (amount != null && amount.isNegative()) {
            throw new IllegalArgumentException("Amount cannot be negative, but was " + amount);
        }
        if (trades < 0) {
            throw new IllegalArgumentException("Number of trades cannot be negative, but was " + trades);
        }
        this.timePeriod = times.timePeriod();
        this.beginTime = times.beginTime();
        this.endTime = times.endTime();
        this.openPrice = openPrice;
        this.highPrice = highPrice;
        this.lowPrice = lowPrice;
        this.closePrice = closePrice;
        this.volume = volume;
        this.amount = amount;
        this.trades = trades;
    }

    /**
     * Validates the OHLC invariant: for every non-null price pair, the high price
     * must be greater than or equal to both the open and the close price, and the
     * low price must be less than or equal to both the open and the close price.
     *
     * @param openPrice  the open price, may be {@code null}
     * @param highPrice  the high price, may be {@code null}
     * @param lowPrice   the low price, may be {@code null}
     * @param closePrice the close price, may be {@code null}
     * @throws IllegalArgumentException if the OHLC invariant is violated
     */
    private static void validatePrices(Num openPrice, Num highPrice, Num lowPrice, Num closePrice) {
        if (highPrice != null && lowPrice != null && highPrice.isLessThan(lowPrice)) {
            throw new IllegalArgumentException(
                    "High price must be greater than or equal to low price, but was " + highPrice + " < " + lowPrice);
        }
        if (highPrice != null && openPrice != null && highPrice.isLessThan(openPrice)) {
            throw new IllegalArgumentException(
                    "High price must be greater than or equal to open price, but was " + highPrice + " < " + openPrice);
        }
        if (highPrice != null && closePrice != null && highPrice.isLessThan(closePrice)) {
            throw new IllegalArgumentException("High price must be greater than or equal to close price, but was "
                    + highPrice + " < " + closePrice);
        }
        if (lowPrice != null && openPrice != null && lowPrice.isGreaterThan(openPrice)) {
            throw new IllegalArgumentException(
                    "Low price must be less than or equal to open price, but was " + lowPrice + " > " + openPrice);
        }
        if (lowPrice != null && closePrice != null && lowPrice.isGreaterThan(closePrice)) {
            throw new IllegalArgumentException(
                    "Low price must be less than or equal to close price, but was " + lowPrice + " > " + closePrice);
        }
    }

    private static ResolvedTimes resolvedTimes(Duration timePeriod, Instant beginTime, Instant endTime) {
        final Duration resolvedTimePeriod;
        if (timePeriod != null) {
            if (beginTime != null && endTime != null
                    && timePeriod.compareTo(Duration.between(beginTime, endTime)) != 0) {
                throw new IllegalArgumentException(
                        "The calculated timePeriod between beginTime and endTime does not match the given timePeriod.");
            }
            resolvedTimePeriod = timePeriod;
        } else if (beginTime != null && endTime != null) {
            resolvedTimePeriod = Duration.between(beginTime, endTime);
        } else {
            throw new NullPointerException("Time period cannot be null");
        }

        final Instant resolvedBeginTime;
        if (beginTime == null && endTime != null) {
            resolvedBeginTime = endTime.minus(resolvedTimePeriod);
        } else if (beginTime != null) {
            resolvedBeginTime = beginTime;
        } else {
            throw new NullPointerException("Begin time cannot be null");
        }

        final Instant resolvedEndTime;
        if (beginTime != null && endTime == null) {
            resolvedEndTime = beginTime.plus(resolvedTimePeriod);
        } else if (endTime != null) {
            resolvedEndTime = endTime;
        } else {
            throw new NullPointerException("End time cannot be null");
        }

        return new ResolvedTimes(resolvedTimePeriod, resolvedBeginTime, resolvedEndTime);
    }

    private record ResolvedTimes(Duration timePeriod, Instant beginTime, Instant endTime) {
    }

    void attachToBarSeries(final BaseBarSeries series, final int index) {
        synchronized (retainingSeries) {
            purgeClearedRetainingSeries();
            final IdentityKey<BaseBarSeries> lookupKey = new IdentityKey<>(series, null);
            final RetainingSeriesRegistration registration = retainingSeries.get(lookupKey);
            if (registration == null) {
                retainingSeries.put(new IdentityKey<>(series, retainingSeriesQueue),
                        new RetainingSeriesRegistration(index));
            } else {
                registration.attach(index);
            }
        }
    }

    void detachFromBarSeries(final BaseBarSeries series, final int index) {
        synchronized (retainingSeries) {
            purgeClearedRetainingSeries();
            final IdentityKey<BaseBarSeries> lookupKey = new IdentityKey<>(series, null);
            final RetainingSeriesRegistration registration = retainingSeries.get(lookupKey);
            if (registration != null && !registration.detach(index)) {
                retainingSeries.remove(lookupKey);
            }
        }
    }

    private void purgeClearedRetainingSeries() {
        IdentityKey<?> cleared;
        while ((cleared = (IdentityKey<?>) retainingSeriesQueue.poll()) != null) {
            retainingSeries.remove(cleared);
        }
    }

    @Override
    public Duration getTimePeriod() {
        return timePeriod;
    }

    @Override
    public Instant getBeginTime() {
        return beginTime;
    }

    @Override
    public Instant getEndTime() {
        return endTime;
    }

    @Override
    public Num getOpenPrice() {
        return openPrice;
    }

    @Override
    public Num getHighPrice() {
        return highPrice;
    }

    @Override
    public Num getLowPrice() {
        return lowPrice;
    }

    @Override
    public Num getClosePrice() {
        return closePrice;
    }

    @Override
    public Num getVolume() {
        return volume;
    }

    @Override
    public Num getAmount() {
        return amount;
    }

    @Override
    public long getTrades() {
        return trades;
    }

    @Override
    public void addTrade(Num tradeVolume, Num tradePrice) {
        applyTrade(tradeVolume, tradePrice);
        publishRetainedBarMutation();
    }

    final synchronized RetainedBarMutationPublication deferAddTrade(final BaseBarSeries origin, final Num tradeVolume,
            final Num tradePrice) {
        final MutationState previousState = MUTATION_STATE.get();
        final MutationState deferredState = new MutationState(this, true);
        Throwable failure = null;
        MUTATION_STATE.set(deferredState);
        try {
            addTrade(tradeVolume, tradePrice);
        } catch (RuntimeException | Error cause) {
            failure = cause;
        } finally {
            if (previousState == null) {
                MUTATION_STATE.remove();
            } else {
                MUTATION_STATE.set(previousState);
            }
        }
        return completeDeferredMutation(previousState, deferredState, origin, failure);
    }

    final synchronized RetainedBarMutationPublication deferAddPrice(final BaseBarSeries origin, final Num price) {
        final MutationState previousState = MUTATION_STATE.get();
        final MutationState deferredState = new MutationState(this, true);
        Throwable failure = null;
        MUTATION_STATE.set(deferredState);
        try {
            addPrice(price);
        } catch (RuntimeException | Error cause) {
            failure = cause;
        } finally {
            if (previousState == null) {
                MUTATION_STATE.remove();
            } else {
                MUTATION_STATE.set(previousState);
            }
        }
        return completeDeferredMutation(previousState, deferredState, origin, failure);
    }

    private RetainedBarMutationPublication completeDeferredMutation(final MutationState previousState,
            final MutationState deferredState, final BaseBarSeries origin, final Throwable failure) {
        final boolean nestedMutation = previousState != null && previousState.defersNestedPublication();
        final RetainedBarMutationPublication primaryPublication = deferredState.publicationCount > 0 || failure != null
                ? captureRetainedBarMutation(deferredState.publicationCount, nestedMutation ? null : origin, failure)
                : null;
        final RetainedBarMutationPublication publication = RetainedBarMutationPublication.combine(primaryPublication,
                deferredState.nestedPublications());
        if (nestedMutation) {
            if (publication != null) {
                previousState.deferNestedPublication(publication);
            }
            return null;
        }
        return publication;
    }

    private MutationState currentMutationState() {
        final MutationState state = MUTATION_STATE.get();
        return state != null && state.bar == this ? state : null;
    }

    /**
     * Applies the common OHLCV update for a trade without publishing its retained
     * bar mutation. Subclasses that add trade fields call this before publishing
     * their complete update.
     */
    @SuppressFBWarnings(value = "AT_NONATOMIC_OPERATIONS_ON_SHARED_VARIABLE", justification = "BaseBar mutators are intentionally mutable; concurrent callers must synchronize at the series boundary.")
    final void applyTrade(Num tradeVolume, Num tradePrice) {
        final MutationState previousState = MUTATION_STATE.get();
        MutationState state = currentMutationState();
        final boolean temporaryState = state == null;
        if (temporaryState) {
            state = new MutationState(this, previousState);
            MUTATION_STATE.set(state);
        }
        try {
            state.suppressionDepth++;
            try {
                addPrice(tradePrice);
            } finally {
                state.suppressionDepth--;
                if (temporaryState) {
                    if (previousState == null) {
                        MUTATION_STATE.remove();
                    } else {
                        MUTATION_STATE.set(previousState);
                    }
                }
            }
            volume = volume.plus(tradeVolume);
            amount = amount.plus(tradeVolume.multipliedBy(tradePrice));
            trades++;
        } catch (RuntimeException | Error failure) {
            // A price hook can change OHLC before throwing. Restore the enclosing
            // scope first so direct and nested calls publish through its sink.
            publishRetainedBarMutationAfterFailure(failure);
            throw failure;
        }
    }

    final void publishRetainedBarMutationAfterFailure(final Throwable failure) {
        try {
            publishRetainedBarMutation();
        } catch (RuntimeException | Error notificationFailure) {
            if (notificationFailure != failure) {
                failure.addSuppressed(notificationFailure);
            }
        }
    }

    /**
     * Adds a price to the bar, folding the existing open and close prices into
     * freshly initialized extrema so the OHLC invariant survives every mutation
     * path.
     */
    @Override
    public void addPrice(Num price) {
        applyTradePrice(price);
        final MutationState state = currentMutationState();
        if (state == null || state.suppressionDepth == 0) {
            publishRetainedBarMutation();
        }
    }

    private void applyTradePrice(Num price) {
        if (openPrice == null) {
            openPrice = price;
        }
        final Num priorClose = closePrice;
        closePrice = price;
        if (highPrice == null) {
            highPrice = price;
            if (openPrice.isGreaterThan(highPrice)) {
                highPrice = openPrice;
            }
            if (priorClose != null && priorClose.isGreaterThan(highPrice)) {
                highPrice = priorClose;
            }
        } else if (highPrice.isLessThan(price)) {
            highPrice = price;
        }
        if (lowPrice == null) {
            lowPrice = price;
            if (openPrice.isLessThan(lowPrice)) {
                lowPrice = openPrice;
            }
            if (priorClose != null && priorClose.isLessThan(lowPrice)) {
                lowPrice = priorClose;
            }
        } else if (lowPrice.isGreaterThan(price)) {
            lowPrice = price;
        }
    }

    /**
     * Validates the deserialized state so serialized bars written by older ta4j
     * versions that predate the OHLC invariant are rejected instead of silently
     * loading inconsistent prices.
     *
     * @param stream the object stream
     * @throws IOException            if deserialization fails
     * @throws ClassNotFoundException if a serialized class is unavailable
     * @throws InvalidObjectException if the serialized prices violate the OHLC
     *                                invariant
     */
    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        stream.defaultReadObject();
        retainingSeries = new HashMap<>();
        retainingSeriesQueue = new ReferenceQueue<>();
        try {
            validatePrices(openPrice, highPrice, lowPrice, closePrice);
        } catch (IllegalArgumentException e) {
            throw new InvalidObjectException("Serialized bar violates the OHLC invariant: " + e.getMessage());
        }
    }

    final void publishRetainedBarMutation() {
        final MutationState state = currentMutationState();
        if (state != null) {
            state.publicationCount++;
            return;
        }
        final MutationState deferredState = MUTATION_STATE.get();
        if (deferredState != null && deferredState.defersNestedPublication()) {
            deferredState.deferNestedPublication(captureRetainedBarMutation(1, null, null));
            return;
        }
        captureRetainedBarMutation(1, null, null).publish();
    }

    private RetainedBarMutationPublication captureRetainedBarMutation(final int publicationCount,
            final BaseBarSeries origin, final Throwable failure) {
        final List<RetainedSeriesMutation> mutations;
        synchronized (retainingSeries) {
            purgeClearedRetainingSeries();
            mutations = new ArrayList<>(retainingSeries.size());
            retainingSeries.forEach((key, registration) -> {
                final BaseBarSeries series = key.get();
                if (series != null) {
                    mutations.add(new RetainedSeriesMutation(series, registration.firstIndex()));
                }
            });
        }
        // The top-level originating series still holds its write lock. Publish its
        // revision before unlock so readers never see a changed terminal bar with
        // an old cache key. Nested mutations pass a null origin and publish all
        // callbacks after the owning operation releases its lock.
        Throwable publicationFailure = failure;
        if (origin != null) {
            for (int index = mutations.size() - 1; index >= 0; index--) {
                final RetainedSeriesMutation mutation = mutations.get(index);
                if (mutation.series() == origin) {
                    for (int publication = 0; publication < publicationCount; publication++) {
                        try {
                            origin.retainedBarMutated(this, mutation.index());
                        } catch (RuntimeException | Error cause) {
                            publicationFailure = RetainedBarMutationPublication.mergeFailures(publicationFailure,
                                    cause);
                        }
                    }
                    mutations.remove(index);
                    break;
                }
            }
        }
        return new RetainedBarMutationPublication(this, mutations, publicationCount, publicationFailure);
    }

    /**
     * @return {end time, close price, open price, low price, high price, volume}
     */
    @Override
    public String toString() {
        return String.format(
                "{end time: %1s, close price: %2s, open price: %3s, low price: %4s high price: %5s, volume: %6s}",
                endTime, closePrice, openPrice, lowPrice, highPrice, volume);
    }

    @Override
    public int hashCode() {
        return Objects.hash(beginTime, endTime, timePeriod, openPrice, highPrice, lowPrice, closePrice, volume, amount,
                trades);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null || getClass() != obj.getClass())
            return false;
        final BaseBar other = (BaseBar) obj;
        return Objects.equals(beginTime, other.beginTime) && Objects.equals(endTime, other.endTime)
                && Objects.equals(timePeriod, other.timePeriod) && Objects.equals(openPrice, other.openPrice)
                && Objects.equals(highPrice, other.highPrice) && Objects.equals(lowPrice, other.lowPrice)
                && Objects.equals(closePrice, other.closePrice) && Objects.equals(volume, other.volume)
                && Objects.equals(amount, other.amount) && trades == other.trades;
    }
}
