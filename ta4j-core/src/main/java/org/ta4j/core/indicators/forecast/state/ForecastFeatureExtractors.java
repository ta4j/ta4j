/*
 * SPDX-License-Identifier: MIT
 */
package org.ta4j.core.indicators.forecast.state;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import org.ta4j.core.criteria.ReturnRepresentation;
import org.ta4j.core.num.Num;

/**
 * Named, representation-bound feature extractors for return-moment models.
 *
 * <p>
 * Values are raw and unscaled. Distance and regression consumers remain
 * responsible for fitting and applying their own training-only standardization.
 *
 * @since 0.23.1
 */
public final class ForecastFeatureExtractors {

    private ForecastFeatureExtractors() {
    }

    /**
     * Extracts {@code [mean, volatility]}.
     *
     * @param representation required return representation
     * @param <S>            state type
     * @return two-feature extractor
     * @since 0.23.1
     */
    public static <S extends ReturnMomentState> ForecastFeatureExtractor<S> meanVolatility(
            ReturnRepresentation representation) {
        String returnUnit = unit(representation);
        return extractor("return-moments/mean-volatility", representation,
                List.of(new ForecastFeatureSchema.Feature("mean", returnUnit),
                        new ForecastFeatureSchema.Feature("volatility", returnUnit)),
                List.of(ReturnMoments::mean, ReturnMoments::volatility));
    }

    /**
     * Extracts {@code [drift, volatility]}.
     *
     * @param representation required return representation
     * @param <S>            state type
     * @return two-feature extractor
     * @since 0.23.1
     */
    public static <S extends ReturnMomentState> ForecastFeatureExtractor<S> driftVolatility(
            ReturnRepresentation representation) {
        String returnUnit = unit(representation);
        return extractor("return-moments/drift-volatility", representation,
                List.of(new ForecastFeatureSchema.Feature("drift", returnUnit),
                        new ForecastFeatureSchema.Feature("volatility", returnUnit)),
                List.of(ReturnMoments::drift, ReturnMoments::volatility));
    }

    /**
     * Extracts {@code [mean, drift, variance]} without duplicating dispersion.
     *
     * @param representation required return representation
     * @param <S>            state type
     * @return three-feature extractor
     * @since 0.23.1
     */
    public static <S extends ReturnMomentState> ForecastFeatureExtractor<S> meanDriftVariance(
            ReturnRepresentation representation) {
        String returnUnit = unit(representation);
        return extractor("return-moments/mean-drift-variance", representation,
                List.of(new ForecastFeatureSchema.Feature("mean", returnUnit),
                        new ForecastFeatureSchema.Feature("drift", returnUnit),
                        new ForecastFeatureSchema.Feature("variance", returnUnit + "^2")),
                List.of(ReturnMoments::mean, ReturnMoments::drift, ReturnMoments::variance));
    }

    /**
     * Extracts the rough-volatility default shape
     * {@code [mean, volatility, roughness_hurst, vol_of_vol]}.
     *
     * <p>
     * Return location and volatility use raw log-return units. Hurst and vol-of-vol
     * are dimensionless because vol-of-vol is measured from the logarithmic
     * volatility proxy.
     *
     * @return four-feature rough-volatility extractor
     * @since 0.23.1
     */
    public static ForecastFeatureExtractor<RoughVolatilityForecastState> roughVolatility() {
        ForecastFeatureSchema schema = new ForecastFeatureSchema("rough-volatility/default", 1,
                ReturnRepresentation.LOG,
                List.of(new ForecastFeatureSchema.Feature("mean", "log-return"),
                        new ForecastFeatureSchema.Feature("volatility", "log-return"),
                        new ForecastFeatureSchema.Feature("roughness_hurst", "dimensionless"),
                        new ForecastFeatureSchema.Feature("vol_of_vol", "dimensionless")));
        return new ForecastFeatureExtractor<>() {
            @Override
            public ForecastFeatureSchema schema() {
                return schema;
            }

            @Override
            public void extractInto(RoughVolatilityForecastState state, double[] target, int offset) {
                RoughVolatilityForecastState value = Objects.requireNonNull(state, "state must not be null");
                double[] destination = Objects.requireNonNull(target, "target must not be null");
                ReturnMoments moments = value.moments();
                if (!moments.isStable() || moments.representation() != ReturnRepresentation.LOG) {
                    throw new IllegalArgumentException("state must contain stable log-return moments");
                }
                if (offset < 0 || offset > destination.length - schema.dimension()) {
                    throw new IndexOutOfBoundsException("target does not have room for the schema at offset");
                }
                destination[offset] = finiteDouble(moments.mean(), "mean");
                destination[offset + 1] = finiteDouble(moments.volatility(), "volatility");
                destination[offset + 2] = finiteDouble(value.roughnessHurst(), "roughness_hurst");
                destination[offset + 3] = finiteDouble(value.volOfVol(), "vol_of_vol");
            }
        };
    }

    /**
     * Extracts the online change-point default shape
     * {@code [mean, volatility, recent_change_probability, most_likely_run_length]}.
     *
     * <p>
     * Return location and volatility use raw log-return units. Recent change
     * probability is a probability in {@code [0, 1]}, and most likely run length is
     * measured in observations.
     *
     * @return four-feature online change-point extractor
     * @since 0.23.1
     */
    public static ForecastFeatureExtractor<OnlineChangePointForecastState> changePoint() {
        ForecastFeatureSchema schema = new ForecastFeatureSchema("change-point/default", 1, ReturnRepresentation.LOG,
                List.of(new ForecastFeatureSchema.Feature("mean", "log-return"),
                        new ForecastFeatureSchema.Feature("volatility", "log-return"),
                        new ForecastFeatureSchema.Feature("recent_change_probability", "probability"),
                        new ForecastFeatureSchema.Feature("most_likely_run_length", "observations")));
        return new ForecastFeatureExtractor<>() {
            @Override
            public ForecastFeatureSchema schema() {
                return schema;
            }

            @Override
            public void extractInto(OnlineChangePointForecastState state, double[] target, int offset) {
                OnlineChangePointForecastState value = Objects.requireNonNull(state, "state must not be null");
                double[] destination = Objects.requireNonNull(target, "target must not be null");
                ReturnMoments moments = value.moments();
                if (!moments.isStable() || moments.representation() != ReturnRepresentation.LOG) {
                    throw new IllegalArgumentException("state must contain stable log-return moments");
                }
                if (offset < 0 || offset > destination.length - schema.dimension()) {
                    throw new IndexOutOfBoundsException("target does not have room for the schema at offset");
                }
                destination[offset] = finiteDouble(moments.mean(), "mean");
                destination[offset + 1] = finiteDouble(moments.volatility(), "volatility");
                destination[offset + 2] = finiteDouble(value.recentChangeProbability(), "recent_change_probability");
                destination[offset + 3] = value.mostLikelyRunLength();
            }
        };
    }

    private static <S extends ReturnMomentState> ForecastFeatureExtractor<S> extractor(String id,
            ReturnRepresentation representation, List<ForecastFeatureSchema.Feature> features,
            List<Function<ReturnMoments, Num>> resolvers) {
        ReturnRepresentation requiredRepresentation = Objects.requireNonNull(representation,
                "representation must not be null");
        ForecastFeatureSchema schema = new ForecastFeatureSchema(id, 1, requiredRepresentation, features);
        List<Function<ReturnMoments, Num>> valueResolvers = List.copyOf(resolvers);
        return new ForecastFeatureExtractor<>() {
            @Override
            public ForecastFeatureSchema schema() {
                return schema;
            }

            @Override
            public void extractInto(S state, double[] target, int offset) {
                S value = Objects.requireNonNull(state, "state must not be null");
                double[] destination = Objects.requireNonNull(target, "target must not be null");
                ReturnMoments moments = value.moments();
                if (moments == null || !moments.isStable()) {
                    throw new IllegalArgumentException("state must be stable");
                }
                if (moments.representation() != requiredRepresentation) {
                    throw new IllegalArgumentException("state representation must match schema representation");
                }
                if (offset < 0 || offset > destination.length - schema.dimension()) {
                    throw new IndexOutOfBoundsException("target does not have room for the schema at offset");
                }
                for (int i = 0; i < valueResolvers.size(); i++) {
                    destination[offset + i] = finiteDouble(valueResolvers.get(i).apply(moments),
                            schema.features().get(i).name());
                }
            }
        };
    }

    private static double finiteDouble(Num value, String fieldName) {
        Num number = Objects.requireNonNull(value, fieldName + " must not be null");
        if (!Num.isFinite(number)) {
            throw new IllegalArgumentException(fieldName + " must be finite");
        }
        double primitive = number.doubleValue();
        if (!Double.isFinite(primitive)) {
            throw new IllegalArgumentException(fieldName + " cannot be represented as a finite double");
        }
        if (primitive == 0d && !number.isZero()) {
            throw new IllegalArgumentException(fieldName + " underflows primitive double precision");
        }
        return primitive;
    }

    private static String unit(ReturnRepresentation representation) {
        return switch (Objects.requireNonNull(representation, "representation must not be null")) {
        case LOG -> "log-return";
        case DECIMAL -> "decimal-return";
        case PERCENTAGE -> "percentage-points";
        case MULTIPLICATIVE -> "multiplicative-return";
        };
    }
}
