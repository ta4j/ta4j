/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.analysis;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.ta4j.core.num.Num;

/**
 * Immutable, producer-owned output from {@link IndicatorFamilyManager}. All
 * lists and mappings preserve the manager's canonical order.
 *
 * @since 0.29.1
 */
public final class IndicatorFamilyResult {
    private final Num similarityThreshold;
    private final int stableIndex;
    private final Map<String, String> familyByIndicator;
    private final List<Family> families;
    private final List<PairSimilarity> pairSimilarities;

    IndicatorFamilyResult(Num similarityThreshold, int stableIndex, Map<String, String> familyByIndicator,
            List<Family> families, List<PairSimilarity> pairSimilarities) {
        this.similarityThreshold = similarityThreshold;
        this.stableIndex = stableIndex;
        this.familyByIndicator = Collections.unmodifiableMap(new LinkedHashMap<>(familyByIndicator));
        this.families = List.copyOf(families);
        this.pairSimilarities = List.copyOf(pairSimilarities);
    }

    /**
     * @return threshold enforced for every internal family pair.
     * @since 0.29.1
     */
    public Num similarityThreshold() {
        return similarityThreshold;
    }

    /**
     * @return first index where all pair metrics are stable, capped at
     *         {@link Integer#MAX_VALUE}
     * @since 0.29.1
     */
    public int stableIndex() {
        return stableIndex;
    }

    /**
     * @return indicator-to-family mapping.
     * @since 0.29.1
     */
    public Map<String, String> familyByIndicator() {
        return familyByIndicator;
    }

    /**
     * @return families in canonical order.
     * @since 0.29.1
     */
    public List<Family> families() {
        return families;
    }

    /**
     * @return pairs in canonical name order, including unavailable measurements.
     * @since 0.29.1
     */
    public List<PairSimilarity> pairSimilarities() {
        return pairSimilarities;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof IndicatorFamilyResult value
                && Objects.equals(similarityThreshold, value.similarityThreshold) && stableIndex == value.stableIndex
                && Objects.equals(familyByIndicator, value.familyByIndicator)
                && Objects.equals(families, value.families) && Objects.equals(pairSimilarities, value.pairSimilarities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(similarityThreshold, stableIndex, familyByIndicator, families, pairSimilarities);
    }

    @Override
    public String toString() {
        return "IndicatorFamilyResult[" + "similarityThreshold=" + similarityThreshold + ", " + "stableIndex="
                + stableIndex + ", " + "familyByIndicator=" + familyByIndicator + ", " + "families=" + families + ", "
                + "pairSimilarities=" + pairSimilarities + "]";
    }

    /**
     * One family of similar indicators.
     *
     * @since 0.29.1
     */
    public static final class Family {
        private final String familyId;
        private final List<String> indicatorNames;
        private final String representativeIndicatorName;
        private final Num averageInternalSimilarity;
        private final Num minimumInternalSimilarity;

        Family(String familyId, List<String> indicatorNames, String representativeIndicatorName,
                Num averageInternalSimilarity, Num minimumInternalSimilarity) {
            this.familyId = familyId;
            this.indicatorNames = List.copyOf(indicatorNames);
            this.representativeIndicatorName = representativeIndicatorName;
            this.averageInternalSimilarity = averageInternalSimilarity;
            this.minimumInternalSimilarity = minimumInternalSimilarity;
        }

        /**
         * @return deterministic family identifier.
         * @since 0.29.1
         */
        public String familyId() {
            return familyId;
        }

        /**
         * @return members in canonical name order.
         * @since 0.29.1
         */
        public List<String> indicatorNames() {
            return indicatorNames;
        }

        /**
         * @return member with strongest mean internal score; canonical name breaks
         *         ties.
         * @since 0.29.1
         */
        public String representativeIndicatorName() {
            return representativeIndicatorName;
        }

        /**
         * @return mean internal pair score; one for singletons.
         * @since 0.29.1
         */
        public Num averageInternalSimilarity() {
            return averageInternalSimilarity;
        }

        /**
         * @return weakest internal pair score, at least the threshold; one for
         *         singletons.
         * @since 0.29.1
         */
        public Num minimumInternalSimilarity() {
            return minimumInternalSimilarity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Family value && Objects.equals(familyId, value.familyId)
                    && Objects.equals(indicatorNames, value.indicatorNames)
                    && Objects.equals(representativeIndicatorName, value.representativeIndicatorName)
                    && Objects.equals(averageInternalSimilarity, value.averageInternalSimilarity)
                    && Objects.equals(minimumInternalSimilarity, value.minimumInternalSimilarity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(familyId, indicatorNames, representativeIndicatorName, averageInternalSimilarity,
                    minimumInternalSimilarity);
        }

        @Override
        public String toString() {
            return "Family[" + "familyId=" + familyId + ", " + "indicatorNames=" + indicatorNames + ", "
                    + "representativeIndicatorName=" + representativeIndicatorName + ", " + "averageInternalSimilarity="
                    + averageInternalSimilarity + ", " + "minimumInternalSimilarity=" + minimumInternalSimilarity + "]";
        }
    }

    /**
     * One pair measurement.
     *
     * @since 0.29.1
     */
    public static final class PairSimilarity {
        private final String firstIndicatorName;
        private final String secondIndicatorName;
        private final Num similarity;
        private final Num signedAverageSimilarity;
        private final Num latestSignedSimilarity;
        private final int sampleCount;
        private final Num minimumSignedSimilarity;
        private final Num maximumSignedSimilarity;

        PairSimilarity(String firstIndicatorName, String secondIndicatorName, Num similarity,
                Num signedAverageSimilarity, Num latestSignedSimilarity, int sampleCount, Num minimumSignedSimilarity,
                Num maximumSignedSimilarity) {
            this.firstIndicatorName = firstIndicatorName;
            this.secondIndicatorName = secondIndicatorName;
            this.similarity = similarity;
            this.signedAverageSimilarity = signedAverageSimilarity;
            this.latestSignedSimilarity = latestSignedSimilarity;
            this.sampleCount = sampleCount;
            this.minimumSignedSimilarity = minimumSignedSimilarity;
            this.maximumSignedSimilarity = maximumSignedSimilarity;
        }

        /**
         * @return first canonical pair name.
         * @since 0.29.1
         */
        public String firstIndicatorName() {
            return firstIndicatorName;
        }

        /**
         * @return second canonical pair name.
         * @since 0.29.1
         */
        public String secondIndicatorName() {
            return secondIndicatorName;
        }

        /**
         * @return mean absolute score in [0, 1], or NaN when sampleCount is zero.
         * @since 0.29.1
         */
        public Num similarity() {
            return similarity;
        }

        /**
         * @return mean signed score, or NaN when unavailable.
         * @since 0.29.1
         */
        public Num signedAverageSimilarity() {
            return signedAverageSimilarity;
        }

        /**
         * @return latest valid signed score, or NaN when unavailable.
         * @since 0.29.1
         */
        public Num latestSignedSimilarity() {
            return latestSignedSimilarity;
        }

        /**
         * @return number of valid observed scores.
         * @since 0.29.1
         */
        public int sampleCount() {
            return sampleCount;
        }

        /**
         * @return minimum observed signed score, or NaN when unavailable.
         * @since 0.29.1
         */
        public Num minimumSignedSimilarity() {
            return minimumSignedSimilarity;
        }

        /**
         * @return maximum observed signed score, or NaN when unavailable.
         * @since 0.29.1
         */
        public Num maximumSignedSimilarity() {
            return maximumSignedSimilarity;
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof PairSimilarity value && Objects.equals(firstIndicatorName, value.firstIndicatorName)
                    && Objects.equals(secondIndicatorName, value.secondIndicatorName)
                    && Objects.equals(similarity, value.similarity)
                    && Objects.equals(signedAverageSimilarity, value.signedAverageSimilarity)
                    && Objects.equals(latestSignedSimilarity, value.latestSignedSimilarity)
                    && sampleCount == value.sampleCount
                    && Objects.equals(minimumSignedSimilarity, value.minimumSignedSimilarity)
                    && Objects.equals(maximumSignedSimilarity, value.maximumSignedSimilarity);
        }

        @Override
        public int hashCode() {
            return Objects.hash(firstIndicatorName, secondIndicatorName, similarity, signedAverageSimilarity,
                    latestSignedSimilarity, sampleCount, minimumSignedSimilarity, maximumSignedSimilarity);
        }

        @Override
        public String toString() {
            return "PairSimilarity[" + "firstIndicatorName=" + firstIndicatorName + ", " + "secondIndicatorName="
                    + secondIndicatorName + ", " + "similarity=" + similarity + ", " + "signedAverageSimilarity="
                    + signedAverageSimilarity + ", " + "latestSignedSimilarity=" + latestSignedSimilarity + ", "
                    + "sampleCount=" + sampleCount + ", " + "minimumSignedSimilarity=" + minimumSignedSimilarity + ", "
                    + "maximumSignedSimilarity=" + maximumSignedSimilarity + "]";
        }
    }
}
